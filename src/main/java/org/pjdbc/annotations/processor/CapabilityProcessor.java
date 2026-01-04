package org.pjdbc.annotations.processor;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import org.pjdbc.annotations.DriverCapability;
import org.pjdbc.annotations.DriverDependency;
import org.pjdbc.annotations.DriverParameter;
import org.pjdbc.annotations.DriverSideEffects;

/**
 * Annotation processor that generates pjdbc.capabilities.json from driver annotations.
 *
 * <p>This processor scans for classes annotated with {@link DriverCapability} and
 * generates a JSON manifest containing all driver metadata. The manifest is written
 * to {@code META-INF/pjdbc.capabilities.json} in the output resources.</p>
 *
 * <p>The processor runs at compile time and ensures the manifest is always in sync
 * with the actual driver implementations.</p>
 */
@SupportedAnnotationTypes("org.pjdbc.annotations.DriverCapability")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class CapabilityProcessor extends AbstractProcessor {

    private static final String MANIFEST_PATH = "pjdbc.capabilities.generated.json";
    private static final String MANIFEST_VERSION = "1.0";

    private Filer filer;
    private Messager messager;
    private boolean processed = false;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.filer = processingEnv.getFiler();
        this.messager = processingEnv.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        // Only process once
        if (processed) {
            return false;
        }

        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(DriverCapability.class);
        if (elements.isEmpty()) {
            return false;
        }

        List<Map<String, Object>> drivers = new ArrayList<>();

        for (Element element : elements) {
            if (!(element instanceof TypeElement)) {
                messager.printMessage(Diagnostic.Kind.WARNING,
                    "@DriverCapability can only be applied to classes", element);
                continue;
            }

            TypeElement typeElement = (TypeElement) element;
            Map<String, Object> driverInfo = processDriver(typeElement);
            if (driverInfo != null) {
                drivers.add(driverInfo);
            }
        }

        if (!drivers.isEmpty()) {
            generateManifest(drivers);
            processed = true;
        }

        return false; // Don't claim the annotations
    }

    private Map<String, Object> processDriver(TypeElement element) {
        DriverCapability capability = element.getAnnotation(DriverCapability.class);
        if (capability == null) {
            return null;
        }

        Map<String, Object> driver = new LinkedHashMap<>();

        // Basic info
        String name = capability.name().isEmpty()
            ? element.getSimpleName().toString()
            : capability.name();
        driver.put("name", name);
        driver.put("prefix", capability.prefix());
        driver.put("class", element.getQualifiedName().toString());
        driver.put("description", capability.description());

        // Capabilities array
        List<String> capabilities = new ArrayList<>();
        for (String cap : capability.capabilities()) {
            capabilities.add(cap);
        }
        driver.put("capabilities", capabilities);

        // Parameters
        List<Map<String, Object>> parameters = processParameters(element);
        driver.put("parameters", parameters);

        // Dependencies
        List<Map<String, Object>> dependencies = processDependencies(element);
        if (!dependencies.isEmpty()) {
            driver.put("dependencies", dependencies);
        }

        // Side effects
        Map<String, Object> sideEffects = processSideEffects(element);
        driver.put("sideEffects", sideEffects);

        // Composability
        driver.put("composable", capability.composable());
        driver.put("terminal", capability.terminal());

        return driver;
    }

    private List<Map<String, Object>> processParameters(TypeElement element) {
        List<Map<String, Object>> parameters = new ArrayList<>();

        DriverParameter[] params = element.getAnnotationsByType(DriverParameter.class);
        for (DriverParameter param : params) {
            Map<String, Object> paramInfo = new LinkedHashMap<>();
            paramInfo.put("name", param.name());
            paramInfo.put("type", param.type().name().toLowerCase());

            if (!param.description().isEmpty()) {
                paramInfo.put("description", param.description());
            }

            if (!param.defaultValue().isEmpty()) {
                // Convert to appropriate type
                Object defaultVal = convertDefault(param.defaultValue(), param.type());
                paramInfo.put("default", defaultVal);
            }

            if (param.min() != Long.MIN_VALUE) {
                paramInfo.put("min", param.min());
            }

            if (param.max() != Long.MAX_VALUE) {
                paramInfo.put("max", param.max());
            }

            if (param.enumValues().length > 0) {
                List<String> enumVals = new ArrayList<>();
                for (String v : param.enumValues()) {
                    enumVals.add(v);
                }
                paramInfo.put("enum", enumVals);
            }

            if (param.required()) {
                paramInfo.put("required", true);
            }

            parameters.add(paramInfo);
        }

        return parameters;
    }

    private Object convertDefault(String value, DriverParameter.ParameterType type) {
        try {
            switch (type) {
                case INTEGER:
                    return Long.parseLong(value);
                case FLOAT:
                    return Double.parseDouble(value);
                case BOOLEAN:
                    return Boolean.parseBoolean(value);
                default:
                    return value;
            }
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private List<Map<String, Object>> processDependencies(TypeElement element) {
        List<Map<String, Object>> dependencies = new ArrayList<>();

        DriverDependency[] deps = element.getAnnotationsByType(DriverDependency.class);
        for (DriverDependency dep : deps) {
            Map<String, Object> depInfo = new LinkedHashMap<>();
            depInfo.put("groupId", dep.groupId());
            depInfo.put("artifactId", dep.artifactId());

            if (!dep.version().isEmpty()) {
                depInfo.put("version", dep.version());
            }

            depInfo.put("optional", dep.optional());

            if (!dep.description().isEmpty()) {
                depInfo.put("description", dep.description());
            }

            dependencies.add(depInfo);
        }

        return dependencies;
    }

    private Map<String, Object> processSideEffects(TypeElement element) {
        Map<String, Object> sideEffects = new LinkedHashMap<>();

        DriverSideEffects effects = element.getAnnotation(DriverSideEffects.class);
        if (effects != null) {
            if (effects.stateful()) sideEffects.put("stateful", true);
            if (effects.logging()) sideEffects.put("logging", true);
            if (effects.network()) sideEffects.put("network", true);
            if (effects.filesystem()) sideEffects.put("filesystem", true);
            if (effects.metrics()) sideEffects.put("metrics", true);
            if (effects.tracing()) sideEffects.put("tracing", true);
            if (effects.modifiesQueries()) sideEffects.put("modifiesQueries", true);
            if (effects.modifiesResults()) sideEffects.put("modifiesResults", true);
        }

        return sideEffects;
    }

    private void generateManifest(List<Map<String, Object>> drivers) {
        try {
            FileObject resource = filer.createResource(
                StandardLocation.CLASS_OUTPUT,
                "",
                MANIFEST_PATH
            );

            try (Writer writer = resource.openWriter()) {
                writeJson(writer, drivers);
            }

            messager.printMessage(Diagnostic.Kind.NOTE,
                "Generated " + MANIFEST_PATH + " with " + drivers.size() + " drivers");

        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                "Failed to generate capabilities manifest: " + e.getMessage());
        }
    }

    private void writeJson(Writer writer, List<Map<String, Object>> drivers) throws IOException {
        writer.write("{\n");
        writer.write("  \"version\": \"" + MANIFEST_VERSION + "\",\n");
        writer.write("  \"drivers\": [\n");

        for (int i = 0; i < drivers.size(); i++) {
            writeDriver(writer, drivers.get(i), i == drivers.size() - 1);
        }

        writer.write("  ]\n");
        writer.write("}\n");
    }

    private void writeDriver(Writer writer, Map<String, Object> driver, boolean isLast) throws IOException {
        writer.write("    {\n");

        List<String> keys = new ArrayList<>(driver.keySet());
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            Object value = driver.get(key);
            writer.write("      \"" + key + "\": ");
            writeValue(writer, value, 6);
            if (i < keys.size() - 1) {
                writer.write(",");
            }
            writer.write("\n");
        }

        writer.write("    }");
        if (!isLast) {
            writer.write(",");
        }
        writer.write("\n");
    }

    @SuppressWarnings("unchecked")
    private void writeValue(Writer writer, Object value, int indent) throws IOException {
        if (value == null) {
            writer.write("null");
        } else if (value instanceof String) {
            writer.write("\"" + escapeJson((String) value) + "\"");
        } else if (value instanceof Boolean || value instanceof Number) {
            writer.write(value.toString());
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.isEmpty()) {
                writer.write("[]");
            } else if (list.get(0) instanceof Map) {
                // Array of objects (like parameters)
                writer.write("[\n");
                for (int i = 0; i < list.size(); i++) {
                    writeIndent(writer, indent + 2);
                    writeObject(writer, (Map<String, Object>) list.get(i), indent + 2);
                    if (i < list.size() - 1) {
                        writer.write(",");
                    }
                    writer.write("\n");
                }
                writeIndent(writer, indent);
                writer.write("]");
            } else {
                // Array of primitives
                writer.write("[");
                for (int i = 0; i < list.size(); i++) {
                    writeValue(writer, list.get(i), indent);
                    if (i < list.size() - 1) {
                        writer.write(", ");
                    }
                }
                writer.write("]");
            }
        } else if (value instanceof Map) {
            writeObject(writer, (Map<String, Object>) value, indent);
        }
    }

    private void writeObject(Writer writer, Map<String, Object> obj, int indent) throws IOException {
        if (obj.isEmpty()) {
            writer.write("{}");
            return;
        }

        writer.write("{\n");
        List<String> keys = new ArrayList<>(obj.keySet());
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            Object value = obj.get(key);
            writeIndent(writer, indent + 2);
            writer.write("\"" + key + "\": ");
            writeValue(writer, value, indent + 2);
            if (i < keys.size() - 1) {
                writer.write(",");
            }
            writer.write("\n");
        }
        writeIndent(writer, indent);
        writer.write("}");
    }

    private void writeIndent(Writer writer, int indent) throws IOException {
        for (int i = 0; i < indent; i++) {
            writer.write(" ");
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
