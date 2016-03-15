package com.freva.masteroppgave.preprocessing.preprocessors;

import com.freva.masteroppgave.preprocessing.filters.Filters;
import com.freva.masteroppgave.utils.MapUtils;

import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

public class TweetFilterer {
    private static final Pattern endsWithNumber = Pattern.compile("[\\.,x][0-9]+$");
    private static List<Function<String, String>> filters = Arrays.asList(
            Filters::removeFreeDigits, Filters::removeRepeatedWhitespace, String::trim, String::toLowerCase);

    /**
     * First stage of raw downloaded tweet filtering. Removes all tweets that cant be used by any other preprocessor.
     * @param input_filename File path to the raw downloaded tweets
     * @param output_filename File path to write the filtered tweets
     * @throws IOException
     */
    public static void rawTweetCleaner(File input_filename, File output_filename) throws IOException {
        final Map<String, Integer> unique = new HashMap<>();
        int lineCounter = 0;

        try(Writer output = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output_filename), "UTF-8"))) {
            try (BufferedReader br = new BufferedReader(new FileReader(input_filename))) {
                for (String line; (line = br.readLine()) != null; lineCounter++) {
                    if (lineCounter % 100000 == 0) {
                        if(lineCounter % 10000000 == 0) MapUtils.removeInfrequentItems(unique, 2);
                        System.out.print("\r" + lineCounter);
                    }
                    if (! shouldInclude(line)) continue;

                    String filtered = Filters.stringChain(line, filters);
                    if(unique.containsKey(filtered)) continue;

                    MapUtils.incrementMapByValue(unique, filtered, 1);
                    output.write(line + "\n");
                }
            }
        }
    }


    private static boolean shouldInclude(String text) {
        if (text.startsWith("RT @")) return false;
        if (text.contains("https://") || text.contains("http://")) return false;
        if (text.startsWith("Get Weather Updates from The Weather Channel")) return false;
        if (text.toLowerCase().contains("harry_styles")) return false;
        if (text.contains("°")) return false;
        if (text.contains("TVPersonality2015")) return false;
        if (endsWithNumber.matcher(text).find()) return false;

        return true;
    }
}
