package org.pjdbc.annotations.processor;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

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
import javax.lang.model.util.Elements;
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

    private static final String MANIFEST_PATH = "pjdbc.capabilities.json";
    private static final String MANIFEST_VERSION = "1.0";

    /** Valid prefix pattern: lowercase letters and numbers, starting with letter */
    private static final Pattern PREFIX_PATTERN = Pattern.compile("^[a-z][a-z0-9]*$");

    /** Valid capability tag pattern: lowercase with optional hyphens */
    private static final Pattern CAPABILITY_PATTERN = Pattern.compile("^[a-z]+(-[a-z]+)*$");

    private Filer filer;
    private Messager messager;
    private Elements elementUtils;
    private boolean processed = false;
    private volatile boolean hasErrors = false;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.filer = processingEnv.getFiler();
        this.messager = processingEnv.getMessager();
        this.elementUtils = processingEnv.getElementUtils();
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
        Map<String, TypeElement> prefixToElement = new HashMap<>();

        for (Element element : elements) {
            if (!(element instanceof TypeElement)) {
                error("@DriverCapability can only be applied to classes", element);
                continue;
            }

            TypeElement typeElement = (TypeElement) element;

            // Validate and process driver
            Map<String, Object> driverInfo = processDriver(typeElement, prefixToElement);
            if (driverInfo != null) {
                drivers.add(driverInfo);
            }
        }

        // Only generate manifest if no errors occurred
        if (!drivers.isEmpty() && !hasErrors) {
            generateManifest(drivers);
            processed = true;
        } else if (hasErrors) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                "Capability manifest generation aborted due to validation errors");
        }

        return false; // Don't claim the annotations
    }

    // ========== Error/Warning Helpers ==========

    private void error(String message, Element element) {
        hasErrors = true;
        messager.printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    private void warning(String message, Element element) {
        messager.printMessage(Diagnostic.Kind.WARNING, message, element);
    }

    private void note(String message) {
        messager.printMessage(Diagnostic.Kind.NOTE, message);
    }

    // ========== Driver Processing ==========

    private Map<String, Object> processDriver(TypeElement element, Map<String, TypeElement> prefixToElement) {
        DriverCapability capability = element.getAnnotation(DriverCapability.class);
        if (capability == null) {
            return null;
        }

        String className = element.getSimpleName().toString();

        // Validate @DriverCapability
        if (!validateDriverCapability(capability, element, prefixToElement)) {
            return null; // Validation errors prevent processing
        }

        Map<String, Object> driver = new LinkedHashMap<>();

        // Basic info
        String name = capability.name().isEmpty() ? className : capability.name();
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

        // Parameters (with validation)
        List<Map<String, Object>> parameters = processParameters(element);
        driver.put("parameters", parameters);

        // Dependencies (with validation)
        List<Map<String, Object>> dependencies = processDependencies(element);
        if (!dependencies.isEmpty()) {
            driver.put("dependencies", dependencies);
        }

        // Side effects
        Map<String, Object> sideEffects = processSideEffects(element);
        driver.put("sideEffects", sideEffects);

        // Composability validation
        if (capability.terminal() && capability.composable()) {
            warning("Driver is marked as both terminal and composable; terminal drivers typically are not composable", element);
        }

        driver.put("composable", capability.composable());
        driver.put("terminal", capability.terminal());

        // Check for potential drift between JavaDocs and annotations
        checkJavaDocDrift(element);

        return driver;
    }

    // ========== Validation Methods ==========

    private boolean validateDriverCapability(DriverCapability capability, TypeElement element,
            Map<String, TypeElement> prefixToElement) {
        boolean valid = true;
        String prefix = capability.prefix();

        // Prefix must not be empty
        if (prefix == null || prefix.trim().isEmpty()) {
            error("@DriverCapability prefix must not be empty", element);
            valid = false;
        } else {
            // Prefix must match pattern
            if (!PREFIX_PATTERN.matcher(prefix).matches()) {
                error("@DriverCapability prefix '" + prefix +
                    "' is invalid; must be lowercase alphanumeric starting with a letter", element);
                valid = false;
            }

            // Check for duplicate prefix
            TypeElement existing = prefixToElement.get(prefix);
            if (existing != null) {
                error("Duplicate driver prefix '" + prefix + "'; already used by " +
                    existing.getQualifiedName(), element);
                valid = false;
            } else {
                prefixToElement.put(prefix, element);
            }
        }

        // Description should not be empty (warning only)
        if (capability.description() == null || capability.description().trim().isEmpty()) {
            warning("@DriverCapability description should not be empty", element);
        }

        // Validate capability tags
        for (String cap : capability.capabilities()) {
            if (cap == null || cap.trim().isEmpty()) {
                error("Capability tag must not be empty", element);
                valid = false;
            } else if (!CAPABILITY_PATTERN.matcher(cap).matches()) {
                error("Capability tag '" + cap +
                    "' is invalid; must be lowercase with optional hyphens (e.g., 'caching', 'load-balancing')", element);
                valid = false;
            }
        }

        return valid;
    }

    private List<Map<String, Object>> processParameters(TypeElement element) {
        List<Map<String, Object>> parameters = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();

        DriverParameter[] params = element.getAnnotationsByType(DriverParameter.class);
        for (DriverParameter param : params) {
            // Validate parameter
            if (!validateParameter(param, element, seenNames)) {
                continue; // Skip invalid parameters but continue processing
            }

            Map<String, Object> paramInfo = new LinkedHashMap<>();
            paramInfo.put("name", param.name());
            paramInfo.put("type", param.type().name().toLowerCase());

            if (!param.description().isEmpty()) {
                paramInfo.put("description", param.description());
            }

            if (!param.defaultValue().isEmpty()) {
                // Convert to appropriate type
                Object defaultVal = convertDefault(param.defaultValue(), param.type(), param, element);
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

    private boolean validateParameter(DriverParameter param, TypeElement element, Set<String> seenNames) {
        boolean valid = true;
        String name = param.name();

        // Name must not be empty
        if (name == null || name.trim().isEmpty()) {
            error("@DriverParameter name must not be empty", element);
            return false;
        }

        // Check for duplicate parameter names
        if (!seenNames.add(name)) {
            error("Duplicate parameter name '" + name + "'", element);
            valid = false;
        }

        // Validate min/max constraints
        if (param.min() != Long.MIN_VALUE && param.max() != Long.MAX_VALUE) {
            if (param.min() > param.max()) {
                error("Parameter '" + name + "' has min (" + param.min() +
                    ") greater than max (" + param.max() + ")", element);
                valid = false;
            }
        }

        // Validate default value against constraints
        if (!param.defaultValue().isEmpty()) {
            validateDefaultValue(param, element);
        }

        // Warning for missing description
        if (param.description().isEmpty()) {
            warning("Parameter '" + name + "' has no description", element);
        }

        return valid;
    }

    private void validateDefaultValue(DriverParameter param, TypeElement element) {
        String defaultValue = param.defaultValue();
        String name = param.name();

        switch (param.type()) {
            case INTEGER:
                try {
                    long value = Long.parseLong(defaultValue);
                    // Check min constraint
                    if (param.min() != Long.MIN_VALUE && value < param.min()) {
                        error("Parameter '" + name + "' default value " + value +
                            " is less than min " + param.min(), element);
                    }
                    // Check max constraint
                    if (param.max() != Long.MAX_VALUE && value > param.max()) {
                        error("Parameter '" + name + "' default value " + value +
                            " is greater than max " + param.max(), element);
                    }
                } catch (NumberFormatException e) {
                    error("Parameter '" + name + "' has invalid integer default value: " + defaultValue, element);
                }
                break;

            case FLOAT:
                try {
                    Double.parseDouble(defaultValue);
                } catch (NumberFormatException e) {
                    error("Parameter '" + name + "' has invalid float default value: " + defaultValue, element);
                }
                break;

            case BOOLEAN:
                if (!defaultValue.equalsIgnoreCase("true") && !defaultValue.equalsIgnoreCase("false")) {
                    warning("Parameter '" + name + "' has non-standard boolean default value: " + defaultValue +
                        " (will be parsed as false)", element);
                }
                break;

            case STRING:
                // Check enum constraint
                if (param.enumValues().length > 0) {
                    List<String> enumList = Arrays.asList(param.enumValues());
                    if (!enumList.contains(defaultValue)) {
                        error("Parameter '" + name + "' default value '" + defaultValue +
                            "' is not in enum values: " + enumList, element);
                    }
                }
                break;
        }
    }

    private Object convertDefault(String value, DriverParameter.ParameterType type,
            DriverParameter param, TypeElement element) {
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
            // Already reported in validateDefaultValue, just return string
            return value;
        }
    }

    private List<Map<String, Object>> processDependencies(TypeElement element) {
        List<Map<String, Object>> dependencies = new ArrayList<>();

        DriverDependency[] deps = element.getAnnotationsByType(DriverDependency.class);
        for (DriverDependency dep : deps) {
            // Validate dependency
            if (!validateDependency(dep, element)) {
                continue;
            }

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

    private boolean validateDependency(DriverDependency dep, TypeElement element) {
        boolean valid = true;

        if (dep.groupId() == null || dep.groupId().trim().isEmpty()) {
            error("@DriverDependency groupId must not be empty", element);
            valid = false;
        }

        if (dep.artifactId() == null || dep.artifactId().trim().isEmpty()) {
            error("@DriverDependency artifactId must not be empty", element);
            valid = false;
        }

        // Warning for missing version
        if (dep.version().isEmpty()) {
            warning("@DriverDependency for " + dep.groupId() + ":" + dep.artifactId() +
                " has no version specified", element);
        }

        return valid;
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

    // ========== JavaDoc Drift Detection ==========

    /**
     * Checks if JavaDoc comments might be drifting from annotation metadata.
     *
     * <p>Warns when:
     * <ul>
     *   <li>JavaDoc contains a "Parameters:" section that may become stale</li>
     *   <li>JavaDoc explicitly lists parameter names that are also in annotations</li>
     *   <li>JavaDoc is missing when the driver has documented parameters</li>
     * </ul>
     *
     * <p>This encourages developers to rely on generated docs from annotations
     * rather than manually maintaining parameter documentation in JavaDocs.
     */
    private void checkJavaDocDrift(TypeElement element) {
        String docComment = elementUtils.getDocComment(element);
        if (docComment == null || docComment.isBlank()) {
            // No JavaDoc at all - check if we should warn
            DriverParameter[] params = element.getAnnotationsByType(DriverParameter.class);
            if (params.length > 0) {
                note("Driver " + element.getSimpleName() +
                    " has @DriverParameter annotations but no JavaDoc. " +
                    "Consider adding a class-level description (parameter docs will be generated).");
            }
            return;
        }

        DriverParameter[] params = element.getAnnotationsByType(DriverParameter.class);
        if (params.length == 0) {
            return; // No parameters to drift
        }

        // Check for manual parameter documentation patterns that may drift
        String docLower = docComment.toLowerCase();

        // Pattern 1: "Parameters:" section
        if (docLower.contains("parameters:") || docLower.contains("* parameters:")) {
            warning("JavaDoc contains 'Parameters:' section that may drift from @DriverParameter annotations. " +
                "Consider removing manual parameter docs - they will be generated from annotations. " +
                "See: org.pjdbc.doclet.CapabilityDoclet",
                element);
        }

        // Pattern 2: Parameter names explicitly listed
        List<String> documentedParams = new ArrayList<>();
        for (DriverParameter param : params) {
            String name = param.name();
            // Check if parameter name appears with explanation patterns
            if (docComment.contains(name + " -") ||
                docComment.contains(name + ":") ||
                docComment.contains("@param " + name)) {
                documentedParams.add(name);
            }
        }

        if (!documentedParams.isEmpty()) {
            warning("JavaDoc manually documents parameters " + documentedParams +
                " which are also declared via @DriverParameter. " +
                "This duplication may lead to drift. " +
                "Consider removing parameter details from JavaDoc - they will be generated from annotations.",
                element);
        }
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
