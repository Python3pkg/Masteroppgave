package com.freva.masteroppgave.preprocessing.preprocessors;

import com.freva.masteroppgave.preprocessing.filters.Filters;
import com.freva.masteroppgave.preprocessing.utils.GatePosTagger;
import java.io.*;


public class TweetTagger {
    /**
     * Runs Gate PoS tagger on all tweets inside a file and writes it to another
     * @param input_filename File path to input file with tweets, one on each line
     * @param output_filename File path to output file
     * @param model_filename File path to Gate PoS tagger model file
     * @throws Exception
     */
    public static void posTagTweets(String input_filename, String output_filename, String model_filename) throws Exception {
        GatePosTagger tagger = new GatePosTagger(model_filename);

        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output_filename), "utf-8"))) {
            try(BufferedReader br = new BufferedReader(new FileReader(input_filename))) {
                for(String line; (line = br.readLine()) != null; ) {
                    line = Filters.chain(line,
                            Filters::HTMLUnescape, Filters::removeUnicodeEmoticons, Filters::normalizeForm,
                            Filters::removeURL, Filters::removeRTTag, Filters::removeHashtag, Filters::removeUsername,
                            Filters::removeEmoticons, Filters::removeInnerWordCharacters, Filters::removeNonSyntacticalText,
                            Filters::fixSyntacticalPunctuationGrammar, Filters::removeRepeatedWhitespace, String::trim);

                    if(line.length() > 0) line = tagger.tagSentence(line);
                    line = Filters.chain(line,
                            Filters::removeNonPosTaggedAlphabeticalText, Filters::removeRepeatedWhitespace, String::trim);

                    writer.write(line + "\n");
                }
            }
        }
    }
}