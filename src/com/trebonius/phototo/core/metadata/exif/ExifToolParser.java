package com.trebonius.phototo.core.metadata.exif;

import com.google.gson.reflect.TypeToken;
import com.trebonius.phototo.helpers.MyGsonBuilder;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ExifToolParser {

    private static final int batchSize = 50;

    public static Map<Path, ExifMetadata> readMetadata(List<Path> filenames) throws IOException {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("windows");
        String applicationName = isWindows ? "exiftool.exe" : "exiftool";
        Map<Path, ExifMetadata> result = new ConcurrentHashMap<>();

        IntStream.range(0, (filenames.size() + batchSize - 1) / batchSize).parallel()
                .mapToObj(i -> filenames.subList(i * batchSize, Math.min(filenames.size(), (i + 1) * batchSize)))
                .forEach((List<Path> filenamesSublist) -> {
                    try {
                        String filenamesCommandLineParameter = filenamesSublist.stream() // Concat all the filenames together, after surrounding them with quotes
                                .map((Path filename) -> "\"" + filename.toString() + "\"")
                                .collect(Collectors.joining(" "));

                        String commandLine = applicationName + " " + filenamesCommandLineParameter + " -j -charset utf-8 -c \"%.8f\"";
                        Process p = Runtime.getRuntime().exec(commandLine);

                        BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
                        String line;
                        StringBuilder builder = new StringBuilder();
                        while ((line = input.readLine()) != null) {
                            builder.append(line).append("\n");
                        }
                        p.waitFor();

                        List<ExifMetadata> metadataList = MyGsonBuilder.getGson().fromJson(builder.toString(), new TypeToken<List<ExifMetadata>>() {
                        }.getType());
                        result.putAll(metadataList.stream().collect(Collectors.toMap(x -> Paths.get(x.getSourceFile()), x -> x)));
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });

        return result;
    }
}