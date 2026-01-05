package org.pjdbc.doclet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;
import jdk.javadoc.doclet.StandardDoclet;

/**
 * Custom Doclet that enhances JavaDocs with driver capability documentation
 * generated from @DriverCapability and @DriverParameter annotations.
 *
 * <p>This Doclet delegates to the StandardDoclet for normal processing, then
 * post-processes the generated HTML to inject parameter tables for driver classes.
 *
 * <p>Usage in pom.xml:
 * <pre>{@code
 * <plugin>
 *   <artifactId>maven-javadoc-plugin</artifactId>
 *   <configuration>
 *     <doclet>org.pjdbc.doclet.CapabilityDoclet</doclet>
 *     <docletArtifact>
 *       <groupId>org.pjdbc</groupId>
 *       <artifactId>pjdbc</artifactId>
 *       <version>${project.version}</version>
 *     </docletArtifact>
 *   </configuration>
 * </plugin>
 * }</pre>
 */
public class CapabilityDoclet implements Doclet {

    private static final String DRIVER_CAPABILITY_ANNOTATION = "org.pjdbc.annotations.DriverCapability";
    private static final String DRIVER_PARAMETER_ANNOTATION = "org.pjdbc.annotations.DriverParameter";
    private static final String DRIVER_PARAMETERS_ANNOTATION = "org.pjdbc.annotations.DriverParameters";

    private final StandardDoclet standardDoclet = new StandardDoclet();
    private Reporter reporter;
    private Path outputDirectory;
    private String outputDirArg = null;  // Captured from -d option

    @Override
    public void init(Locale locale, Reporter reporter) {
        this.reporter = reporter;
        standardDoclet.init(locale, reporter);
    }

    @Override
    public String getName() {
        return "CapabilityDoclet";
    }

    @Override
    public Set<? extends Option> getSupportedOptions() {
        // Wrap the standard doclet options to intercept -d
        Set<Option> options = new java.util.HashSet<>();
        for (Option stdOpt : standardDoclet.getSupportedOptions()) {
            if (stdOpt.getNames().contains("-d")) {
                // Wrap the -d option to capture the output directory
                options.add(new Option() {
                    @Override
                    public int getArgumentCount() {
                        return stdOpt.getArgumentCount();
                    }

                    @Override
                    public String getDescription() {
                        return stdOpt.getDescription();
                    }

                    @Override
                    public Kind getKind() {
                        return stdOpt.getKind();
                    }

                    @Override
                    public List<String> getNames() {
                        return stdOpt.getNames();
                    }

                    @Override
                    public String getParameters() {
                        return stdOpt.getParameters();
                    }

                    @Override
                    public boolean process(String option, List<String> arguments) {
                        // Capture the output directory
                        if (!arguments.isEmpty()) {
                            outputDirArg = arguments.get(0);
                        }
                        // Delegate to standard doclet
                        return stdOpt.process(option, arguments);
                    }
                });
            } else {
                options.add(stdOpt);
            }
        }
        return options;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean run(DocletEnvironment docEnv) {
        // Find the output directory from options
        findOutputDirectory();

        // First, run the standard doclet to generate normal JavaDocs
        boolean success = standardDoclet.run(docEnv);

        if (success && outputDirectory != null) {
            // Post-process HTML files to inject parameter documentation
            postProcessDriverDocs(docEnv);
        }

        return success;
    }

    private void findOutputDirectory() {
        // Use the captured -d option value if available
        if (outputDirArg != null && !outputDirArg.isEmpty()) {
            outputDirectory = Path.of(outputDirArg);
            reporter.print(Diagnostic.Kind.NOTE,
                "CapabilityDoclet: Output directory set to " + outputDirectory);
        } else {
            // Fallback: try common Maven locations
            Path[] candidates = {
                Path.of("target/reports/apidocs"),
                Path.of("target/site/apidocs"),
                Path.of("target/apidocs")
            };
            for (Path candidate : candidates) {
                if (Files.exists(candidate)) {
                    outputDirectory = candidate;
                    break;
                }
            }
            if (outputDirectory == null) {
                outputDirectory = Path.of(System.getProperty("user.dir"));
            }
        }
    }

    private void postProcessDriverDocs(DocletEnvironment docEnv) {
        int processedCount = 0;
        int driversFound = 0;

        reporter.print(Diagnostic.Kind.NOTE,
            "CapabilityDoclet: Starting post-processing with output dir: " + outputDirectory);

        for (Element element : docEnv.getIncludedElements()) {
            if (element instanceof TypeElement typeElement) {
                AnnotationMirror capabilityAnnotation = findAnnotation(typeElement, DRIVER_CAPABILITY_ANNOTATION);
                if (capabilityAnnotation != null) {
                    driversFound++;
                    List<AnnotationMirror> paramAnnotations = findAnnotations(typeElement, DRIVER_PARAMETER_ANNOTATION);

                    reporter.print(Diagnostic.Kind.NOTE,
                        "CapabilityDoclet: Found driver " + typeElement.getSimpleName() +
                        " with " + paramAnnotations.size() + " parameters");

                    if (!paramAnnotations.isEmpty()) {
                        try {
                            injectParameterDocs(typeElement, capabilityAnnotation, paramAnnotations);
                            processedCount++;
                        } catch (IOException e) {
                            reporter.print(Diagnostic.Kind.WARNING,
                                "Failed to enhance docs for " + typeElement.getQualifiedName() + ": " + e.getMessage());
                        }
                    }
                }
            }
        }

        reporter.print(Diagnostic.Kind.NOTE,
            "CapabilityDoclet: Found " + driversFound + " drivers, enhanced " + processedCount + " files");
    }

    private AnnotationMirror findAnnotation(TypeElement element, String annotationName) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (mirror.getAnnotationType().toString().equals(annotationName)) {
                return mirror;
            }
        }
        return null;
    }

    private List<AnnotationMirror> findAnnotations(TypeElement element, String annotationName) {
        List<AnnotationMirror> result = new ArrayList<>();
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            String typeName = mirror.getAnnotationType().toString();
            if (typeName.equals(annotationName)) {
                // Direct single annotation
                result.add(mirror);
            } else if (typeName.equals(DRIVER_PARAMETERS_ANNOTATION)) {
                // Container annotation for repeatable @DriverParameter
                for (var entry : mirror.getElementValues().entrySet()) {
                    if (entry.getKey().getSimpleName().toString().equals("value")) {
                        @SuppressWarnings("unchecked")
                        List<AnnotationValue> values = (List<AnnotationValue>) entry.getValue().getValue();
                        for (AnnotationValue av : values) {
                            if (av.getValue() instanceof AnnotationMirror am) {
                                result.add(am);
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    private void injectParameterDocs(TypeElement typeElement,
                                      AnnotationMirror capability,
                                      List<AnnotationMirror> parameters) throws IOException {
        // Build path to HTML file
        String qualifiedName = typeElement.getQualifiedName().toString();
        String relativePath = qualifiedName.replace('.', '/') + ".html";
        Path htmlFile = outputDirectory.resolve(relativePath);

        if (!Files.exists(htmlFile)) {
            return;
        }

        String html = Files.readString(htmlFile, StandardCharsets.UTF_8);

        // Generate the parameter documentation HTML
        String parameterHtml = generateParameterSection(capability, parameters);

        // Inject after the class description, before method summary
        // Look for the description block end or method summary start
        String injectedHtml = injectIntoHtml(html, parameterHtml);

        Files.writeString(htmlFile, injectedHtml, StandardCharsets.UTF_8);
    }

    private String generateParameterSection(AnnotationMirror capability, List<AnnotationMirror> parameters) {
        StringBuilder sb = new StringBuilder();

        // Extract prefix from capability
        String prefix = getAnnotationValue(capability, "prefix", "");

        sb.append("\n<!-- Generated by CapabilityDoclet from @DriverParameter annotations -->\n");
        sb.append("<section class=\"driver-parameters\">\n");
        sb.append("<h2>Driver Parameters</h2>\n");
        sb.append("<p>URL format: <code>jdbc:").append(escapeHtml(prefix));
        sb.append("[param=value,...]:&lt;target-url&gt;</code></p>\n");
        sb.append("<table class=\"summary-table\">\n");
        sb.append("<thead>\n");
        sb.append("<tr>\n");
        sb.append("<th>Parameter</th>\n");
        sb.append("<th>Type</th>\n");
        sb.append("<th>Default</th>\n");
        sb.append("<th>Constraints</th>\n");
        sb.append("<th>Description</th>\n");
        sb.append("</tr>\n");
        sb.append("</thead>\n");
        sb.append("<tbody>\n");

        for (AnnotationMirror param : parameters) {
            String name = getAnnotationValue(param, "name", "");
            String type = getAnnotationValue(param, "type", "STRING");
            String defaultValue = getAnnotationValue(param, "defaultValue", "");
            String description = getAnnotationValue(param, "description", "");
            boolean required = Boolean.parseBoolean(getAnnotationValue(param, "required", "false"));
            long min = Long.parseLong(getAnnotationValue(param, "min", String.valueOf(Long.MIN_VALUE)));
            long max = Long.parseLong(getAnnotationValue(param, "max", String.valueOf(Long.MAX_VALUE)));
            String enumValuesStr = getAnnotationValue(param, "enumValues", "");

            // Extract just the type name (e.g., "INTEGER" from enum constant)
            if (type.contains(".")) {
                type = type.substring(type.lastIndexOf('.') + 1);
            }

            // Build constraints string
            StringBuilder constraints = new StringBuilder();
            if (required) {
                constraints.append("<strong>required</strong>");
            }
            if (min != Long.MIN_VALUE || max != Long.MAX_VALUE) {
                if (constraints.length() > 0) constraints.append(", ");
                if (min != Long.MIN_VALUE && max != Long.MAX_VALUE) {
                    constraints.append(min).append(" - ").append(max);
                } else if (min != Long.MIN_VALUE) {
                    constraints.append("&ge; ").append(min);
                } else {
                    constraints.append("&le; ").append(max);
                }
            }
            if (!enumValuesStr.isEmpty() && !enumValuesStr.equals("{}")) {
                if (constraints.length() > 0) constraints.append(", ");
                constraints.append("one of: ").append(escapeHtml(enumValuesStr));
            }

            sb.append("<tr>\n");
            sb.append("<td><code>").append(escapeHtml(name)).append("</code></td>\n");
            sb.append("<td>").append(escapeHtml(type.toLowerCase())).append("</td>\n");
            sb.append("<td>").append(defaultValue.isEmpty() ? "-" : "<code>" + escapeHtml(defaultValue) + "</code>").append("</td>\n");
            sb.append("<td>").append(constraints.length() > 0 ? constraints.toString() : "-").append("</td>\n");
            sb.append("<td>").append(escapeHtml(description)).append("</td>\n");
            sb.append("</tr>\n");
        }

        sb.append("</tbody>\n");
        sb.append("</table>\n");
        sb.append("</section>\n");
        sb.append("<!-- End CapabilityDoclet generated content -->\n\n");

        return sb.toString();
    }

    private String getAnnotationValue(AnnotationMirror annotation, String key, String defaultValue) {
        for (var entry : annotation.getElementValues().entrySet()) {
            ExecutableElement element = entry.getKey();
            if (element.getSimpleName().toString().equals(key)) {
                Object value = entry.getValue().getValue();
                if (value instanceof List<?> list) {
                    // Array value (e.g., enumValues)
                    StringBuilder sb = new StringBuilder();
                    for (Object item : list) {
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(item.toString());
                    }
                    return sb.toString();
                }
                return value.toString();
            }
        }
        return defaultValue;
    }

    private String injectIntoHtml(String html, String parameterSection) {
        // Try to inject before the method summary or after class description
        // JDK 17+ javadoc structure uses specific section markers

        // Pattern to find the end of the class description block
        Pattern descriptionEnd = Pattern.compile(
            "(</section>\\s*)?(<section class=\"(method-summary|constructor-summary|field-summary))",
            Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = descriptionEnd.matcher(html);
        if (matcher.find()) {
            int insertPos = matcher.start();
            return html.substring(0, insertPos) + parameterSection + html.substring(insertPos);
        }

        // Fallback: inject before </main> or </body>
        Pattern fallback = Pattern.compile("(</main>|</body>)", Pattern.CASE_INSENSITIVE);
        matcher = fallback.matcher(html);
        if (matcher.find()) {
            int insertPos = matcher.start();
            return html.substring(0, insertPos) + parameterSection + html.substring(insertPos);
        }

        // Last resort: append to end
        return html + parameterSection;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}
