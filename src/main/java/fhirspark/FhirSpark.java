package fhirspark;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import fhirspark.resolver.HgncGeneName;
import fhirspark.resolver.OncoKbDrug;
import fhirspark.restmodel.CbioportalRest;
import fhirspark.restmodel.Deletions;
import fhirspark.restmodel.GeneticAlteration;
import fhirspark.restmodel.Mtb;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.ws.rs.core.Cookie;
import org.eclipse.jetty.http.HttpStatus;
import spark.Request;

import static spark.Spark.delete;
import static spark.Spark.get;
import static spark.Spark.options;
import static spark.Spark.port;
import static spark.Spark.post;
import static spark.Spark.put;

/**
 * Fhirspark-Application that stores MTB decisions from cBioPortal and is able
 * to transfer those to hospital information systems via HL7 Version 2 and HL7
 * FHIR.
 */
public final class FhirSpark {

    private static JsonFhirMapper jsonFhirMapper;
    private static JsonHl7v2Mapper jsonHl7v2Mapper;
    private static Client client = new Client();
    private static ObjectMapper objectMapper = new ObjectMapper(new JsonFactory());

    private FhirSpark() {
    }

    /**
     *
     * @param args args[0] can contain a path to a custom configuration yaml file.
     * @throws Exception Exception if the REST API runs into issues.
     */
    public static void main(final String[] args) throws Exception {
        InputStream settingsYaml = ClassLoader.getSystemClassLoader().getResourceAsStream("settings.yaml");
        if (args.length == 1) {
            settingsYaml = new FileInputStream(args[0]);
        }
        ConfigurationLoader configLoader = new ConfigurationLoader();
        final Settings settings = configLoader.loadConfiguration(settingsYaml, Settings.class);
        HgncGeneName.initialize(settings.getHgncPath());
        OncoKbDrug.initalize(settings.getOncokbPath());
        jsonFhirMapper = new JsonFhirMapper(settings);
        if (settings.getHl7v2config() != null && settings.getHl7v2config().get(0).getSendv2()) {
            jsonHl7v2Mapper = new JsonHl7v2Mapper(settings);
        }

        port(settings.getPort());

        options("/mtb/:patientId", (req, res) -> {
            res.status(HttpStatus.NO_CONTENT_204);
            res.header("Access-Control-Allow-Credentials", "true");
            res.header("Access-Control-Allow-Headers", req.headers("Access-Control-Request-Headers"));
            res.header("Access-Control-Allow-Methods", "GET, PUT, DELETE");
            res.header("Access-Control-Allow-Origin", req.headers("Origin"));
            res.header("Content-Length", "0");
            res.header("Vary", "Origin, Access-Control-Request-Headers");
            res.header("Content-Type", "");
            return res;
        });

        /**
        *
        * Checks whether the client has permission to view and manipulate the data of the given patientId
        *
        * @param req Incoming Java Spark Request
        * @param patientId requested patientId
        * @return FORBIDDEN_403 if not authorized
        * @return OK_200 if authorized
        */
        get("/mtb/:patientId/permission", (req, res) -> {
            if (settings.getLoginRequired()
                && (!validateRequest(req, settings) || !validateManipulation(req, settings))) {
                res.status(HttpStatus.FORBIDDEN_403);
                return res;
            }
            res.status(HttpStatus.ACCEPTED_202);
            res.header("Access-Control-Allow-Credentials", "true");
            res.header("Access-Control-Allow-Origin", req.headers("Origin"));
            res.header("Cache-Control", "no-cache, no-store, max-age=0");
            return res;
        });

        get("/mtb/:patientId", (req, res) -> {
            if (settings.getLoginRequired() && !validateRequest(req, settings)) {
                res.status(HttpStatus.FORBIDDEN_403);
                return res;
            }
            res.status(HttpStatus.OK_200);
            res.header("Access-Control-Allow-Credentials", "true");
            res.header("Access-Control-Allow-Origin", req.headers("Origin"));
            res.type("application/json");
            res.header("Vary", "Origin, Access-Control-Request-Headers");
            res.body(jsonFhirMapper.toJson(req.params(":patientId")));
            return res.body();
        });

        put("/mtb/:patientId", (req, res) -> {
            if (settings.getLoginRequired()
                && (!validateRequest(req, settings) || !validateManipulation(req, settings))) {
                res.status(HttpStatus.FORBIDDEN_403);
                return res;
            }
            res.status(HttpStatus.CREATED_201);
            res.header("Access-Control-Allow-Credentials", "true");
            res.header("Access-Control-Allow-Origin", req.headers("Origin"));
            res.type("application/json");
            res.header("Vary", "Origin, Access-Control-Request-Headers");

            List<Mtb> mtbs = objectMapper.readValue(req.body(), CbioportalRest.class).getMtbs();
            jsonFhirMapper.addOrEditMtb(req.params(":patientId"), mtbs);
            if (settings.getHl7v2config().get(0).getSendv2()) {
                jsonHl7v2Mapper.toHl7v2Oru(req.params(":patientId"), mtbs);
            }
            res.body(req.body());
            return res.body();
        });

        delete("/mtb/:patientId", (req, res) -> {
            if (settings.getLoginRequired()
                && (!validateRequest(req, settings) || !validateManipulation(req, settings))) {
                res.status(HttpStatus.FORBIDDEN_403);
                return res;
            }
            res.status(HttpStatus.OK_200);
            res.header("Access-Control-Allow-Credentials", "true");
            res.header("Access-Control-Allow-Origin", req.headers("Origin"));
            res.type("application/json");
            res.header("Vary", "Origin, Access-Control-Request-Headers");
            Deletions deletions = objectMapper.readValue(req.body(), Deletions.class);
            jsonFhirMapper.deleteEntries(req.params(":patientId"), deletions);
            res.body(req.body());
            return res.body();
        });

        options("/mtb/alteration", (req, res) -> {
            res.status(HttpStatus.NO_CONTENT_204);
            res.header("Access-Control-Allow-Credentials", "true");
            res.header("Access-Control-Allow-Headers", req.headers("Access-Control-Request-Headers"));
            res.header("Access-Control-Allow-Methods", "POST");
            res.header("Access-Control-Allow-Origin", req.headers("Origin"));
            res.header("Content-Length", "0");
            res.header("Vary", "Origin, Access-Control-Request-Headers");
            res.header("Content-Type", "");
            return res;
        });

        post("/mtb/alteration", (req, res) -> {
            res.status(HttpStatus.OK_200);
            res.header("Access-Control-Allow-Credentials", "true");
            res.header("Access-Control-Allow-Origin", req.headers("Origin"));
            res.type("application/json");
            res.header("Vary", "Origin, Access-Control-Request-Headers");
            List<GeneticAlteration> alterations = objectMapper.readValue(req.body(),
                    new TypeReference<List<GeneticAlteration>>() {
                    });
            res.body(
                    objectMapper.writeValueAsString(jsonFhirMapper
                        .getTherapyRecommendationsByAlteration(alterations)));
            return res.body();
        });

        options("/mtb/alteration/pmid", (req, res) -> {
            res.status(HttpStatus.NO_CONTENT_204);
            res.header("Access-Control-Allow-Credentials", "true");
            res.header("Access-Control-Allow-Headers", req.headers("Access-Control-Request-Headers"));
            res.header("Access-Control-Allow-Methods", "POST");
            res.header("Access-Control-Allow-Origin", req.headers("Origin"));
            res.header("Content-Length", "0");
            res.header("Vary", "Origin, Access-Control-Request-Headers");
            res.header("Content-Type", "");
            return res;
        });

        post("/mtb/alteration/pmid", (req, res) -> {
            res.status(HttpStatus.OK_200);
            res.header("Access-Control-Allow-Credentials", "true");
            res.header("Access-Control-Allow-Origin", req.headers("Origin"));
            res.type("application/json");
            res.header("Vary", "Origin, Access-Control-Request-Headers");
            List<GeneticAlteration> alterations = objectMapper.readValue(req.body(),
                    new TypeReference<List<GeneticAlteration>>() {
                    });
            res.body(objectMapper.writeValueAsString(jsonFhirMapper.getPmidsByAlteration(alterations)));
            return res.body();
        });

    }

    /**
     * Checks if the session id is authorized to access the clinical data of the patient.
     *
     * @param req Incoming Java Spark Request
     * @return Boolean if the session if able to access the data
     */
    private static boolean validateRequest(Request req, Settings settings) {
        String portalDomain = settings.getPortalUrl();
        String validatePath = "api/studies/" + settings.getMtbStudy() + "/patients/"
                + req.params(":patientId");
        String requestUrl = portalDomain + validatePath;

        WebResource webResource = client.resource(requestUrl);
        WebResource.Builder builder = webResource.getRequestBuilder();
        builder = builder.cookie(new Cookie("JSESSIONID", req.cookies().get("JSESSIONID")));
        ClientResponse response = builder.accept("application/json").get(ClientResponse.class);

        System.out.println(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        System.out.println("Validation request for study:");
        System.out.println("Sending request at requestUrl: " + requestUrl);

        if (response.getStatus() == HttpStatus.OK_200) {
            System.out.println("Response code was good: " + response.getStatus() + "\n");
            return true;
        }
        System.out.println("Response code was: " + response.getStatus() + "\n");
        return false;
    }

    /**
     * Checks if the session id is authorized to manipulate the clinical data of the patients in the MTB study.
     *
     * @param req Incoming Java Spark Request
     * @return Boolean if the session is able to access the data
     */
    private static boolean validateManipulation(Request req, Settings settings) {
        String requestedPatientId = req.params(":patientId");
        String mtbStudy = settings.getMtbStudy();
        String userRoles = req.headers("X-USERROLES");
        String userLoginName = req.headers("X-USERLOGIN");

        System.out.println(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        System.out.println("Manipulation permission request:\nfrom user: " + userLoginName + ", for patientId: "
            + requestedPatientId + "\nfound header X-USERROLES: " + userRoles);

        if (userRoles == null || userRoles.isEmpty()) {
            System.out.println("Incoming user roles are null or empty - returning false\n");
            return false;
        }

        ArrayList<String> roleList = new ArrayList<String>();
        Pattern p = Pattern.compile("\"([^\"]*)\"");
        Matcher m = p.matcher(userRoles);
        while (m.find()) {
            roleList.add(m.group(1));
        }

        for (String s : roleList) {
            if (s.equals(mtbStudy) || s.equals(requestedPatientId)) {
                System.out.println("permission granted with role: " + s + "\n");
                return true;
            }
        }

        System.out.println("no matching role could be found - returning false\n");
        return false;

    }

}
