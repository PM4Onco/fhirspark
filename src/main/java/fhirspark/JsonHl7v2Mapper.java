package fhirspark;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.app.Connection;
import ca.uhn.hl7v2.llp.LLPException;
import ca.uhn.hl7v2.model.DataTypeException;
import ca.uhn.hl7v2.model.v281.datatype.CWE;
import ca.uhn.hl7v2.model.v281.datatype.ST;
import ca.uhn.hl7v2.model.v281.group.ORU_R01_PATIENT_RESULT;
import ca.uhn.hl7v2.model.v281.message.ORU_R01;
import ca.uhn.hl7v2.model.v281.message.RDE_O11;
import ca.uhn.hl7v2.model.v281.segment.NTE;
import ca.uhn.hl7v2.model.v281.segment.OBR;
import ca.uhn.hl7v2.model.v281.segment.OBX;
import ca.uhn.hl7v2.model.v281.segment.PID;
import ca.uhn.hl7v2.model.v281.segment.RXO;
import ca.uhn.hl7v2.model.v281.segment.SPM;
import fhirspark.resolver.HgncGeneName;
import fhirspark.resolver.PubmedPublication;
import fhirspark.resolver.model.Genenames;
import fhirspark.restmodel.Mtb;
import fhirspark.restmodel.TherapyRecommendation;
import java.io.IOException;
import java.util.List;

/**
 * Fulfils a mapping to the HL7 Version 2 standard and transfers the message to a configured target.
 */
public class JsonHl7v2Mapper {

    private HapiContext context = new DefaultHapiContext();
    private Connection connection;
    private PubmedPublication pubmedResolver = new PubmedPublication();

    public JsonHl7v2Mapper(Settings settings) throws HL7Exception {
        this.connection = context.newClient(settings.getHl7v2config().get(0).getServer(),
                settings.getHl7v2config().get(0).getPort(), false);
    }

    /**
     *
     * @param patientId id of the patient.
     * @param mtbs mtb entries of the patient.
     * @throws HL7Exception General Exception.
     * @throws IOException Network Exception.
     * @throws LLPException Exception when sending message.
     */
    public void toHl7v2Oru(String patientId, List<Mtb> mtbs) throws HL7Exception, IOException, LLPException {
        ORU_R01 oru = new ORU_R01();
        oru.initQuickstart("ORU", "R01", "P");

        RDE_O11 rde = new RDE_O11();
        rde.initQuickstart("RDE", "O11", "P");

        for (Mtb mtb : mtbs) {

            // Send only finished MTB results
            if (mtb.getMtbState() == null || !mtb.getMtbState().toUpperCase().equals("COMPLETED")) {
                continue;
            }

            for (TherapyRecommendation therapyRecommendation : mtb.getTherapyRecommendations()) {
                ORU_R01_PATIENT_RESULT result = oru.insertPATIENT_RESULT(oru.getPATIENT_RESULTReps());
                result.getPATIENT().getPID().getPid1_SetIDPID().setValue("1");
                result.getPATIENT().getPID()
                        .getPatientIdentifierList(result.getPATIENT().getPID().getPatientIdentifierListReps())
                        .getIDNumber().setValue(patientId);

                int therapyRecommendationOrder = result.getORDER_OBSERVATIONReps();

                OBR masterPanel = result.getORDER_OBSERVATION(therapyRecommendationOrder).getOBR();
                masterPanel.getSetIDOBR().setValue(String.valueOf(result.getORDER_OBSERVATIONReps()));
                masterPanel.getResultStatus().setValue("F");
                masterPanel.getUniversalServiceIdentifier().getIdentifier().setValue("81247-9");
                masterPanel.getUniversalServiceIdentifier().getText()
                        .setValue("Master HL7 genetic variant reporting panel");
                masterPanel.getUniversalServiceIdentifier().getNameOfCodingSystem().setValue("LN");
                masterPanel.getUniversalServiceIdentifier().getCodingSystemOID().setValue("2.16.840.1.113883.6.1");
                masterPanel.getFillerOrderNumber().getEntityIdentifier().setValue(therapyRecommendation.getId());

                masterPanel.getObservationDateTime().setValue(mtb.getDate().replaceAll("-", ""));

                mtb.getSamples().forEach(sample -> {
                    try {
                        SPM specimen = result.getORDER_OBSERVATION(therapyRecommendationOrder)
                                .getSPECIMEN(result.getORDER_OBSERVATION(therapyRecommendationOrder).getSPECIMENReps())
                                .getSPM();
                        specimen.getSetIDSPM().setValue(String.valueOf(
                                result.getORDER_OBSERVATION(result.getORDER_OBSERVATIONReps() - 1).getSPECIMENReps()));
                        specimen.getSpecimenID().getFillerAssignedIdentifier().getEntityIdentifier().setValue(sample);
                        specimen.getSpecimenType().getIdentifier().setValue("TUMOR");
                        specimen.getSpecimenType().getText().setValue("Tumor");
                    } catch (DataTypeException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                });

                masterPanel.getFillerOrderNumber().getEntityIdentifier().setValue(mtb.getId());

                OBX evidence = result.getORDER_OBSERVATION(therapyRecommendationOrder)
                        .getOBSERVATION(result.getORDER_OBSERVATION(therapyRecommendationOrder).getOBSERVATIONReps())
                        .getOBX();
                evidence.getSetIDOBX().setValue(
                        String.valueOf(result.getORDER_OBSERVATION(therapyRecommendationOrder).getOBSERVATIONReps()));
                evidence.getValueType().setValue("ST");
                evidence.getObservationIdentifier().getIdentifier().setValue("93044-6");
                evidence.getObservationIdentifier().getText().setValue("Level of evidence");
                evidence.getObservationIdentifier().getCodingSystemOID().setValue("2.16.840.1.113883.6.1");
                evidence.getObservationIdentifier().getNameOfCodingSystem().setValue("LN");
                ST evidenceValue = new ST(oru);
                evidenceValue.setValue(therapyRecommendation.getEvidenceLevel());
                evidence.insertObservationValue(0).setData(evidenceValue);

                therapyRecommendation.getReasoning().getGeneticAlterations().forEach(g -> {
                    try {
                        int orderNumber = result.getORDER_OBSERVATIONReps();
                        OBR variant = result.insertORDER_OBSERVATION(orderNumber).getOBR();
                        variant.getSetIDOBR().setValue(String.valueOf(result.getORDER_OBSERVATIONReps()));
                        variant.getUniversalServiceIdentifier().getIdentifier().setValue("81250-3");
                        variant.getUniversalServiceIdentifier().getText().setValue("Discrete genetic variant panel");
                        variant.getUniversalServiceIdentifier().getNameOfCodingSystem().setValue("LN");
                        variant.getUniversalServiceIdentifier().getCodingSystemOID().setValue("2.16.840.1.113883.6.1");

                        OBX variantAssessment = result.getORDER_OBSERVATION(orderNumber).getOBSERVATION(0).getOBX();
                        variantAssessment.getSetIDOBX().setValue(String.valueOf(1));
                        variantAssessment.getObservationIdentifier().getIdentifier().setValue("69548-6");
                        variantAssessment.getObservationIdentifier().getText().setValue("Genetic variant assessment");
                        variantAssessment.getObservationIdentifier().getNameOfCodingSystem().setValue("LN");
                        variantAssessment.getObservationIdentifier().getCodingSystemOID()
                                .setValue("2.16.840.1.113883.6.1");
                        variantAssessment.getValueType().setValue("CWE");
                        CWE variantAssessmentValue = new CWE(oru);
                        variantAssessmentValue.getNameOfCodingSystem().setValue("LN");
                        variantAssessmentValue.getCodingSystemOID().setValue("2.16.840.1.113883.6.1");
                        variantAssessmentValue.getText().setValue("Present");
                        variantAssessmentValue.getIdentifier().setValue("LA9633-4");
                        variantAssessment.insertObservationValue(0).setData(variantAssessmentValue);

                        OBX hgvs = result.getORDER_OBSERVATION(orderNumber).getOBSERVATION(1).getOBX();
                        hgvs.getSetIDOBX().setValue(String.valueOf(2));
                        hgvs.getObservationIdentifier().getIdentifier().setValue("48005-3");
                        hgvs.getObservationIdentifier().getText().setValue("Amino acid change (pHGVS)");
                        hgvs.getObservationIdentifier().getNameOfCodingSystem().setValue("LN");
                        hgvs.getObservationIdentifier().getCodingSystemOID().setValue("2.16.840.1.113883.6.1");
                        hgvs.getValueType().setValue("CWE");
                        CWE hgvsValue = new CWE(oru);
                        hgvsValue.getCodingSystemOID().setValue("2.16.840.1.113883.6.282");
                        hgvsValue.getText().setValue("p." + g.getAlteration());
                        hgvsValue.getIdentifier().setValue("p." + g.getAlteration());
                        hgvs.insertObservationValue(0).setData(hgvsValue);

                        OBX entrez = result.getORDER_OBSERVATION(orderNumber).getOBSERVATION(2).getOBX();
                        entrez.getSetIDOBX().setValue(String.valueOf(3));
                        entrez.getObservationIdentifier().getIdentifier().setValue("81252-9");
                        entrez.getObservationIdentifier().getText().setValue("Discrete genetic variant");
                        entrez.getObservationIdentifier().getNameOfCodingSystem().setValue("LN");
                        entrez.getObservationIdentifier().getCodingSystemOID().setValue("2.16.840.1.113883.6.1");
                        entrez.getValueType().setValue("CWE");
                        CWE entrezValue = new CWE(oru);
                        entrezValue.getCodingSystemOID().setValue("2.16.840.1.113883.4.642.3.1041");
                        entrezValue.getText().setValue(String.valueOf(g.getEntrezGeneId()));
                        entrezValue.getIdentifier().setValue(String.valueOf(g.getEntrezGeneId()));
                        entrez.insertObservationValue(0).setData(entrezValue);

                        OBX hgnc = result.getORDER_OBSERVATION(orderNumber).getOBSERVATION(3).getOBX();
                        hgnc.getSetIDOBX().setValue(String.valueOf(4));
                        hgnc.getObservationIdentifier().getIdentifier().setValue("48018-6");
                        hgnc.getObservationIdentifier().getText().setValue("Gene studied [ID]");
                        hgnc.getObservationIdentifier().getNameOfCodingSystem().setValue("LN");
                        hgnc.getObservationIdentifier().getCodingSystemOID().setValue("2.16.840.1.113883.6.1");
                        hgnc.getValueType().setValue("CWE");
                        CWE hgncValue = new CWE(oru);
                        Genenames genenames = HgncGeneName.resolve(g.getEntrezGeneId());
                        hgncValue.getCodingSystemOID().setValue("2.16.840.1.113883.6.281");
                        hgncValue.getIdentifier().setValue(genenames.getHgncId());
                        hgncValue.getText().setValue(genenames.getApprovedSymbol());
                        hgnc.insertObservationValue(0).setData(hgncValue);
                    } catch (HL7Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

                });

                therapyRecommendation.getReferences().forEach(reference -> {
                    try {
                        CWE v2ref = new CWE(oru);
                        v2ref.getCodingSystemOID().setValue("2.16.840.1.113883.13.191");
                        v2ref.getIdentifier().setValue(String.valueOf(reference.getPmid()));
                        String name = reference.getName() != null ? reference.getName()
                                : pubmedResolver.resolvePublication(reference.getPmid());
                        v2ref.getText().setValue(name);
                    } catch (DataTypeException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                });

                therapyRecommendation.getTreatments().forEach(treatment -> {

                    try {
                        PID pid = rde.getPATIENT().getPID();
                        pid.getPid1_SetIDPID().setValue("1");
                        pid.getPatientIdentifierList(pid.getPatientIdentifierListReps()).getIDNumber()
                                .setValue(patientId);
                        RXO rxo = rde.getORDER(rde.getORDERReps()).getORDER_DETAIL().getRXO();
                        rxo.getDispensingPharmacy().getCodingSystemOID().setValue("2.16.840.1.113883.3.26.1.1");
                        rxo.getDispensingPharmacy().getIdentifier().setValue(treatment.getNcitCode());
                        rxo.getDispensingPharmacy().getText().setValue(treatment.getName());
                    } catch (DataTypeException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

                });

                NTE generealRecommendation = result.getORDER_OBSERVATION(therapyRecommendationOrder).getNTE(0);
                generealRecommendation.getSetIDNTE().setValue("1");
                generealRecommendation.getSourceOfComment().setValue("L");
                generealRecommendation.getCommentType().getIdentifier().setValue("GI");
                generealRecommendation.getCommentType().getText().setValue("General Instructions");
                generealRecommendation.getComment(0).setValue(mtb.getGeneralRecommendation());

                NTE comments = result.getORDER_OBSERVATION(therapyRecommendationOrder).getNTE(1);
                comments.getSetIDNTE().setValue("2");
                comments.getSourceOfComment().setValue("L");
                comments.getCommentType().getIdentifier().setValue("1R");
                comments.getCommentType().getText().setValue("Primary Reason");
                therapyRecommendation.getComment().forEach(comment -> {
                    try {
                        comments.getComment(therapyRecommendation.getComment().indexOf(comment)).setValue(comment);
                    } catch (DataTypeException e1) {
                        // TODO Auto-generated catch block
                        e1.printStackTrace();
                    }
                });

                result.getORDER_OBSERVATIONAll().forEach(order -> {
                    try {
                        order.getOBSERVATIONAll().forEach(observation -> {
                            try {
                                observation.getOBX().insertResponsibleObserver(0).getPersonIdentifier()
                                        .setValue(therapyRecommendation.getAuthor());
                            } catch (HL7Exception e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                        });
                    } catch (HL7Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                });

            }

        }

        connection.getInitiator().sendAndReceive(oru.getMessage());
        connection.getInitiator().sendAndReceive(rde.getMessage());

    }

}
