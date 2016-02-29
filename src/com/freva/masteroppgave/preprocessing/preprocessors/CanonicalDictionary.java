package com.freva.masteroppgave.preprocessing.preprocessors;


import com.freva.masteroppgave.preprocessing.filters.CanonicalForm;
import com.freva.masteroppgave.preprocessing.filters.Filters;
import com.freva.masteroppgave.preprocessing.filters.RegexFilters;
import com.freva.masteroppgave.utils.FileUtils;
import com.freva.masteroppgave.utils.JSONUtils;
import com.freva.masteroppgave.utils.MapUtils;
import com.freva.masteroppgave.utils.progressbar.Progressable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class CanonicalDictionary implements Progressable {
    private static final double correctFrequency = 0.05;
    private static final double termFrequency = 0.0005;
    private TweetReader tweetReader;


    /**
     * Creates canonical dictionary:
     * Words that are reduced to the same canonical form are grouped together, the frequent words are kept in the final
     * dictionary. F.ex. "god" => ["good", "god"].
     * @param input File with words to base dictionary off
     * @param output File to write dictionary to
     * @throws IOException
     */
    public void createCanonicalDictionary(File input, File output) throws IOException {
        tweetReader = new TweetReader(input,
                Filters::HTMLUnescape, Filters::removeUnicodeEmoticons, Filters::normalizeForm, Filters::removeURL,
                Filters::removeRTTag, Filters::removeHashtag, Filters::removeUsername, Filters::removeEmoticons,
                Filters::removeInnerWordCharacters, Filters::removeNonAlphanumericalText, Filters::removeFreeDigits,
                Filters::removeRepeatedWhitespace, String::trim, String::toLowerCase);

        int iteration = 0;
        Map<String, Map<String, Integer>> counter = new HashMap<>();
        while(tweetReader.hasNext()) {
            if(iteration++ % 100000 == 0) removeInfrequent(counter, (int) (iteration*termFrequency/2), correctFrequency/2);
            String tweet = tweetReader.readAndPreprocessNextTweet();

            for(String word: RegexFilters.WHITESPACE.split(tweet)) {
                String reduced = CanonicalForm.reduceToCanonicalForm(word);
                if(! counter.containsKey(reduced)) {
                    counter.put(reduced, new HashMap<>());
                }

                MapUtils.incrementMapByValue(counter.get(reduced), word, 1);
            }
        }


        removeInfrequent(counter, (int) (iteration*termFrequency), correctFrequency);
        Map<String, Set<String>> options = new HashMap<>();
        for(Map.Entry<String, Map<String, Integer>> entry: counter.entrySet()) {
            options.put(entry.getKey(), entry.getValue().keySet());
        }
        String json = JSONUtils.toJSON(options, true);
        FileUtils.writeToFile(output, json);
    }


    private static void removeInfrequent(Map<String, Map<String, Integer>> counter, int termLimit, double cutoff) {
        Iterator<Map.Entry<String, Map<String, Integer>>> canonicals = counter.entrySet().iterator();

        while(canonicals.hasNext()) {
            Map.Entry<String, Map<String, Integer>> canonical = canonicals.next();
            Iterator<Map.Entry<String, Integer>> originals = canonical.getValue().entrySet().iterator();
            int termCounter = canonical.getValue().values().stream().mapToInt(Integer::intValue).sum();

            if (termCounter < termLimit) {
                canonicals.remove();
                continue;
            }

            while(originals.hasNext()) {
                if(originals.next().getValue() < termCounter*cutoff) {
                    originals.remove();
                }
            }

            if(canonical.getValue().size() == 1 && canonical.getValue().keySet().contains(canonical.getKey())) {
                canonicals.remove();
            }
        }
    }

    @Override
    public double getProgress() {
        return tweetReader != null ? tweetReader.getProgress() : 0;
    }
}