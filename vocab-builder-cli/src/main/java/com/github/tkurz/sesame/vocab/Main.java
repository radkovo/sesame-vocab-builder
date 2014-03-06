package com.github.tkurz.sesame.vocab;

import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.openrdf.model.util.GraphUtilException;
import org.openrdf.rio.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

/**
 * ...
 * <p/>
 * @author  Thomas Kurz (tkurz@apache.org)
 * @author  Jakob Frank (jakob@apache.org)
 */
public class Main {

    public static void main(String [] args) {
        try {
            CommandLineParser parser = new PosixParser();
            CommandLine cli = parser.parse(getCliOpts(), args);

            if (cli.hasOption('h')) {
                printHelp();
                return;
            }

            // two args must be left over: <input-inputFile> <output-inputFile>
            String[] cliArgs = cli.getArgs();
            final String input, output;
            switch (cliArgs.length) {
                case 0:
                    throw new ParseException("Missing input-file");
                case 1:
                    input = cliArgs[0];
                    output = null;
                    break;
                case 2:
                    input = cliArgs[0];
                    output = cliArgs[1];
                    break;
                default:
                    throw new ParseException("too many arguments");
            }

            RDFFormat format = Rio.getParserFormatForMIMEType(cli.getOptionValue('f', null));

            Path tempFile = null;
            final VocabBuilder builder;
            if (input.startsWith("http://")) {
                tempFile = Files.createTempFile("vocab-builder", "."+(format!=null?format.getDefaultFileExtension():"cache"));
                URL url = new URL(input);

                try {
                    fetchVocab(url, tempFile);
                } catch (URISyntaxException e) {
                    throw new ParseException("Invalid input URL: " +e.getMessage());
                }

                builder = new VocabBuilder(tempFile.toString(), format);
            } else
                builder = new VocabBuilder(input, format);

            if (cli.hasOption('p')) {
                builder.setPackageName(cli.getOptionValue('p'));
            }
            if (cli.hasOption('n')) {
                builder.setName(cli.getOptionValue('n'));
            }
            if (cli.hasOption('u')) {
                builder.setPrefix(cli.getOptionValue('u'));
            }
            if (cli.hasOption('l')) {
                builder.setPreferredLanguage(cli.getOptionValue('l'));
            }
            if (cli.hasOption('s')) {
                try {
                    builder.setIndent(StringUtils.repeat(' ', Integer.parseInt(cli.getOptionValue('s', "4"))));
                } catch (NumberFormatException e) {
                    throw new ParseException("indent must be numeric");
                }
            } else {
                builder.setIndent("\t");
            }

            if (output != null) {
                System.out.printf("Starting generation%n");
                Path outFile = Paths.get(output);
                builder.generate(outFile);
                System.out.printf("Generation finished, result available in '%s'%n", output);
            } else {
                builder.generate(System.out);
            }

            if (tempFile != null) {
                Files.deleteIfExists(tempFile);
            }
        } catch (UnsupportedRDFormatException e) {
            System.err.printf("%s%nTry setting the format explicitly%n", e.getMessage());
        } catch (ParseException e) {
            printHelp(e.getMessage());
        } catch (RDFParseException e) {
            System.err.println("Could not parse input file: " + e.getMessage());
        } catch (FileNotFoundException e) {
            System.err.println("Could not read input-file: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Error during file-access: " + e.getMessage());
        } catch (GraphUtilException e) {
            e.printStackTrace();
        } catch (GenerationException e) {
            System.err.println(e.getMessage());
        }
    }

    private static void printHelp() {
        printHelp(null);
    }

    private static void printHelp(String error) {
        HelpFormatter hf = new HelpFormatter();
        PrintWriter w = new PrintWriter(System.out);
        if (error != null) {
            hf.printWrapped(w, 80, error);
            w.println();
        }
        hf.printWrapped(w, 80, "usage: Main [options...] <input-file> [<output-file>]");
        hf.printWrapped(w, 80, "  <input-file>                the input file to read from");
        hf.printWrapped(w, 80, "  [<output-file>]             the output file to write, StdOut if omitted");
        hf.printOptions(w, 80, getCliOpts(), 2, 2);
        w.flush();
        w.close();
    }

    @SuppressWarnings({"AccessStaticViaInstance", "static-access"})
    private static Options getCliOpts() {
        Options o = new Options();

        o.addOption(OptionBuilder
                .withLongOpt("format")
                .withDescription("mime-type of the input file (will try to guess if absent)")
                .hasArgs(1)
                .withArgName("input-format")
                .isRequired(false)
                .create('f'));

        o.addOption(OptionBuilder
                .withLongOpt("package")
                .withDescription("package declaration (will use default (empty) package if absent")
                .hasArgs(1)
                .withArgName("package")
                .isRequired(false)
                .create('p'));

        o.addOption(OptionBuilder
                .withLongOpt("name")
                .withDescription("the name of the namespace (will try to guess from the input file if absent)")
                .hasArgs(1)
                .withArgName("ns")
                .isRequired(false)
                .create('n'));

        o.addOption(OptionBuilder
                .withLongOpt("uri")
                .withDescription("the prefix for the vocabulary (if not available in the input file)")
                .hasArgs(1)
                .withArgName("prefix")
                .isRequired(false)
                .create('u'));

        o.addOption(OptionBuilder
                .withArgName("spaces")
                .hasOptionalArgs(1)
                .withArgName("indent")
                .withDescription("use spaces for for indentation (tabs if missing, 4 spaces if no number given)")
                .isRequired(false)
                .create('s'));

        o.addOption(OptionBuilder
                .withLongOpt("language")
                .withDescription("preferred language for vocabulary labels")
                .hasArgs(1)
                .withArgName("preferred-language")
                .isRequired(false)
                .create('l'));

        o.addOption(OptionBuilder
                .withLongOpt("help")
                .withDescription("pint this help")
                .isRequired(false)
                .hasArg(false)
                .create('h'));

        return o;
    }

    private static File fetchVocab(URL url, final Path tempFile) throws URISyntaxException, IOException {
        final Properties buildProperties = getBuildProperties();
        final HttpClientBuilder clientBuilder = HttpClientBuilder.create()
                .setUserAgent(
                        String.format("%s:%s/%s (%s)",
                                buildProperties.getProperty("groupId", "unknown"),
                                buildProperties.getProperty("artifactId", "unknown"),
                                buildProperties.getProperty("version", "unknown"),
                                buildProperties.getProperty("name", "unknown"))
                );

        try(CloseableHttpClient client = clientBuilder.build()) {
            final HttpUriRequest request = RequestBuilder.get()
                    .setUri(url.toURI())
                    .setHeader(HttpHeaders.ACCEPT, getAcceptHeaderValue())
                    .build();

            return client.execute(request, new ResponseHandler<File>() {
                @Override
                public File handleResponse(HttpResponse response) throws IOException {
                    final File cf = tempFile.toFile();
                    FileUtils.copyInputStreamToFile(response.getEntity().getContent(), cf);
                    return cf;
                }
            });
        }
    }

    private static Properties getBuildProperties() {
        Properties p = new Properties();
        try {
            p.load(Main.class.getResourceAsStream("/build.properties"));
        } catch (IOException e) {
            // ignore
        }
        return p;
    }

    private static String getAcceptHeaderValue() {
        final Set<RDFFormat> rdfFormats = RDFParserRegistry.getInstance().getKeys();
        final Iterator<String> acceptParams = RDFFormat.getAcceptParams(rdfFormats, false, RDFFormat.TURTLE).iterator();
        if (acceptParams.hasNext()) {
            final StringBuilder sb = new StringBuilder();
            while (acceptParams.hasNext()) {
                sb.append(acceptParams.next());
                if (acceptParams.hasNext()) {
                    sb.append(", ");
                }
            }
            return sb.toString();
        } else {
            return null;
        }
    }

}