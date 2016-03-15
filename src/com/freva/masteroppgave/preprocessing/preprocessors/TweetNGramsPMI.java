package com.freva.masteroppgave.preprocessing.preprocessors;

import com.freva.masteroppgave.preprocessing.filters.Filters;
import com.freva.masteroppgave.preprocessing.filters.RegexFilters;
import com.freva.masteroppgave.preprocessing.filters.WordFilters;
import com.freva.masteroppgave.utils.progressbar.Progressable;
import com.freva.masteroppgave.utils.reader.LineReader;
import com.freva.masteroppgave.utils.tools.Parallel;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;


public class TweetNGramsPMI implements Progressable {
    private LineReader tweetReader;
    private NGramTree nGramTree;

    /**
     * Finds all frequent n-grams in a file, treating each new line as a new document.
     * @param input File with documents to generate n-grams for
     * @param n Maximum n-gram length
     * @param frequencyCutoff Smallest required frequency to include n-gram
     * @param filters List of filters to apply to document before generating n-grams
     * @return Map of n-grams as key and number of occurrences as value
     * @throws IOException
     */
    public final Map<String, Double> getFrequentNGrams(File input, int n, double frequencyCutoff, Filters filters) throws IOException {
        final AtomicInteger lineCounter = new AtomicInteger(0);
        tweetReader = new LineReader(input);
        nGramTree = new NGramTree();

        Parallel.For(tweetReader, tweet -> {
            synchronized (lineCounter) {
                if (lineCounter.incrementAndGet() % 200000 == 0) {
                    nGramTree.pruneInfrequent((int) (frequencyCutoff * lineCounter.intValue()) / 8);
                }
            }

            tweet = filters.apply(tweet);
            for(String sentence : RegexFilters.SENTENCE_END_PUNCTUATION.split(tweet)) {
                String[] tokens = RegexFilters.WHITESPACE.split(sentence.trim());
                if(tokens.length == 1) continue;

                for(int i=0; i<tokens.length; i++) {
                    nGramTree.incrementNGram(Arrays.copyOfRange(tokens, i, Math.min(i+n, tokens.length)));
                }
            }
        });

        return nGramTree.getNGrams((int) (frequencyCutoff*lineCounter.intValue()));
    }


    @Override
    public double getProgress() {
        return tweetReader != null ? tweetReader.getProgress() : 0;
    }


    private class NGramTree {
        private Node root = new Node("");

        private synchronized void incrementNGram(String[] nGram) {
            Node current = root;
            current.numOccurrences++;

            for(String word: nGram) {
                if(! current.hasChild(word)) {
                    current.addChild(word);
                }

                current = current.getChild(word);
                current.numOccurrences++;
            }
        }

        private Node getNode(String phrase) {
            Node current = root;
            for(String word: RegexFilters.WHITESPACE.split(phrase)) {
                if(! current.hasChild(word)) {
                    return null;
                }

                current = current.getChild(word);
            }
            return current;
        }

        private void pruneInfrequent(int limit) {
            root.pruneInfrequent(limit);
        }

        private Map<String, Double> getNGrams(int limit) {
            Map<String, Double> nGrams = new HashMap<>();

            for(Node child: root.children.values()) {
                if(child.numOccurrences >= limit) {
                    nGrams.put(child.phrase, (double) child.numOccurrences);
                }

                child.addFrequentPhrases(nGrams, limit, child.phrase);
            }

            Iterator<Map.Entry<String, Double>> iterator = nGrams.entrySet().iterator();
            while(iterator.hasNext()) {
                Map.Entry<String, Double> next = iterator.next();
                String[] nGramTokens = RegexFilters.WHITESPACE.split(next.getKey());

                if(next.getValue() < 0 ||
                        WordFilters.containsIntensifier(nGramTokens) ||
                        WordFilters.isStopWord(nGramTokens[nGramTokens.length - 1])) {
                    iterator.remove();
                }
            }

            return nGrams;
        }
    }


    private class Node {
        private Map<String, Node> children = new HashMap<>();
        private String phrase;
        private int numOccurrences;
        private double logScore = Double.NaN;

        public Node(String phrase){
            this.phrase = phrase;
        }

        public boolean hasChild(String value) {
            return children.containsKey(value);
        }

        public void addChild(String value) {
            children.put(value, new Node(value));
        }

        public Node getChild(String value) {
            return children.get(value);
        }

        public double getLogScore() {
            if(Double.isNaN(logScore)) logScore = Math.log(numOccurrences);
            return logScore;
        }

        private void pruneInfrequent(int limit) {
            Iterator<Map.Entry<String, Node>> iterator = children.entrySet().iterator();
            while (iterator.hasNext()){
                Map.Entry<String, Node> child = iterator.next();

                if (child.getValue().numOccurrences < limit) {
                    iterator.remove();
                } else {
                    child.getValue().pruneInfrequent(limit);
                }
            }
        }

        private void addFrequentPhrases(Map<String, Double> map, int limit, String prefix) {
            for(Node child: children.values()) {
                if(child.numOccurrences >= limit) {
                    Node lastWord = nGramTree.getNode(child.phrase);

                    if(lastWord != null && lastWord.numOccurrences >= limit) {
                        double temp = nGramTree.root.getLogScore() + child.getLogScore() - getLogScore() - lastWord.getLogScore();

                        String candidate = prefix + " " + child.phrase;
                        map.put(candidate, temp);
                        child.addFrequentPhrases(map, limit, candidate);
                    }
                }
            }
        }
    }
}