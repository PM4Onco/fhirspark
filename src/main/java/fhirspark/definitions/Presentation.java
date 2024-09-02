package fhirspark.definitions;

import ca.uhn.fhir.model.api.annotation.Block;
import ca.uhn.fhir.model.api.annotation.Child;
import ca.uhn.fhir.model.api.annotation.Extension;
import ca.uhn.fhir.model.api.annotation.ResourceDef;
import ca.uhn.fhir.util.ElementUtil;
import org.hl7.fhir.r4.model.BackboneElement;
import org.hl7.fhir.r4.model.Basic;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.StringType;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

@ResourceDef(name = "Basic", profile = "http://example.com/StructureDefinition/mtb-presentation")
public class Presentation extends Basic {
    @Serial
    private static final long serialVersionUID = 1L;

    @Child(name = "slides")
    @Extension(url = "http://example.com/StructureDefinition/mtb-presentation-node", definedLocally = false)
    private List<Node> nodes;

    public List<Node> getNodes() {
        if (nodes == null) {
            return new ArrayList<>();
        }
        return this.nodes;
    }

    public void setNodes(List<Node> nodes) {
        this.nodes = nodes;
    }

    @Override
    public boolean isEmpty() {
        return super.isEmpty() && nodes.isEmpty();
    }

    @Block
    public static class Node extends BackboneElement {
        private static final long serialVersionUID = 1792249628585619265L;

        @Child(name = "slideId")
        @Extension(url = "http://example.com/StructureDefinition/mtb-presentation-slide-id", definedLocally = false)
        private StringType slideId;

        @Child(name = "nodeId")
        @Extension(url = "http://example.com/StructureDefinition/mtb-presentation-node-id", definedLocally = false)
        private StringType nodeId;

        @Child(name = "left")
        @Extension(url = "http://example.com/StructureDefinition/mtb-presentation-node-left", definedLocally = false)
        private IntegerType left;

        @Child(name = "top")
        @Extension(url = "http://example.com/StructureDefinition/mtb-presentation-node-top", definedLocally = false)
        private IntegerType top;

        @Child(name = "width")
        @Extension(url = "http://example.com/StructureDefinition/mtb-presentation-node-width", definedLocally = false)
        private IntegerType width;

        @Child(name = "type")
        @Extension(url = "http://example.com/StructureDefinition/mtb-presentation-node-type", definedLocally = false)
        private StringType type;

        @Child(name = "value")
        @Extension(url = "http://example.com/StructureDefinition/mtb-presentation-node-value", definedLocally = false)
        private StringType value;

        public Node() {
            super();
        }

        public Node(StringType slideId, StringType nodeId, IntegerType left, IntegerType top, IntegerType width, StringType type, StringType value) {
            super();
            this.slideId = slideId;
            this.nodeId = nodeId;
            this.left = left;
            this.top = top;
            this.width = width;
            this.type = type;
            this.value = value;
        }

        @Override
        public Node copy() {
            return new Node(slideId, nodeId, left, top, width, type, value);
        }

        @Override
        public boolean isEmpty() {
            return super.isEmpty() && ElementUtil.isEmpty(slideId, nodeId, left, top, width, type, value);
        }

        public StringType getSlideId() {
            if (slideId == null) {
                return new StringType();
            }
            return slideId;
        }

        public void setSlideId(StringType slideId) {
            this.slideId = slideId;
        }

        public StringType getNodeId() {
            if (nodeId == null) {
                return new StringType();
            }
            return nodeId;
        }

        public void setNodeId(StringType nodeId) {
            this.nodeId = nodeId;
        }

        public IntegerType getLeft() {
            if (left == null) {
                return new IntegerType();
            }

            return left;
        }

        public void setLeft(IntegerType left) {
            this.left = left;
        }

        public IntegerType getTop() {
            if (top == null) {
                return new IntegerType();
            }

            return top;
        }

        public void setTop(IntegerType top) {
            this.top = top;
        }

        public IntegerType getWidth() {
            if (width == null) {
                return new IntegerType();
            }

            return width;
        }

        public void setWidth(IntegerType width) {
            this.width = width;
        }

        public StringType getType() {
            if (type == null) {
                return new StringType();
            }

            return type;
        }

        public void setType(StringType type) {
            this.type = type;
        }

        public StringType getValue() {
            if (value == null) {
                return new StringType();
            }

            return value;
        }

        public void setValue(StringType value) {
            this.value = value;
        }
    }
}
