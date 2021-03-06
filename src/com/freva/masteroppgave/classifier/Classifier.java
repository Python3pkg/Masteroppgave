package com.freva.masteroppgave.classifier;

import com.freva.masteroppgave.classifier.sentence.LexicalParser;
import com.freva.masteroppgave.classifier.sentence.LexicalToken;
import com.freva.masteroppgave.classifier.ClassifierOptions.Variable;
import com.freva.masteroppgave.lexicon.container.TokenTrie;
import com.freva.masteroppgave.lexicon.container.PriorPolarityLexicon;
import com.freva.masteroppgave.preprocessing.filters.Filters;
import com.freva.masteroppgave.utils.reader.DataSetReader.Classification;

import java.util.List;

public class Classifier {
    private final PriorPolarityLexicon lexicon;
    private final TokenTrie phraseTree;
    private final Filters filters;

    public Classifier(PriorPolarityLexicon lexicon, Filters filters) {
        this.lexicon = lexicon;
        this.filters = filters;
        this.phraseTree = new TokenTrie(lexicon.getSubjectiveWords());
    }

    public Classifier(PriorPolarityLexicon lexicon) {
        this(lexicon, null);
    }

    /**
     * Classifies the tweet into one of three classes (negative, neutral or positive) depending on the sentiment value
     * of the tweet and the thresholds specified in the ClassifierOptions
     *
     * @param tweet String tweet to classify
     * @return Sentiment classification (negative, neutral or positive)
     */
    public Classification classify(String tweet) {
        final double sentimentValue = calculateSentiment(tweet);

        return Classification.classifyFromThresholds(sentimentValue,
                ClassifierOptions.getVariable(Variable.CLASSIFICATION_THRESHOLD_LOWER),
                ClassifierOptions.getVariable(Variable.CLASSIFICATION_THRESHOLD_HIGHER));
    }

    public double calculateSentiment(String tweet) {
        if (filters != null) {
            tweet = filters.apply(tweet);
        }

        List<LexicalToken> lexicalTokens = LexicalParser.lexicallyParseTweet(tweet, phraseTree);
        for (int i = 0; i < lexicalTokens.size(); i++) {
            LexicalToken token = lexicalTokens.get(i);
            String phrase = token.getPhrase();

            if (lexicon.hasToken(phrase)) {
                token.setLexicalValue(lexicon.getTokenPolarity(phrase));

            } else if (ClassifierOptions.isNegation(phrase)) {
                propagateNegation(lexicalTokens, i);

            } else if (ClassifierOptions.isIntensifier(phrase)) {
                intensifyNext(lexicalTokens, i, ClassifierOptions.getIntensifierValue(phrase));
            }
        }
        return lexicalTokens.stream().mapToDouble(LexicalToken::getSentimentValue).sum();
    }

    private void propagateNegation(List<LexicalToken> lexicalTokens, int index) {
        final double negationScopeLength = ClassifierOptions.getVariable(ClassifierOptions.Variable.NEGATION_SCOPE_LENGTH);
        for (int i = index + 1; i <= index + negationScopeLength && i < lexicalTokens.size(); i++) {
            lexicalTokens.get(i).setInNegatedContext(true);
            if (lexicalTokens.get(i).isAtTheEndOfSentence()) {
                break;
            }
        }
    }

    private void intensifyNext(List<LexicalToken> lexicalTokens, int index, double intensification) {
        if (!lexicalTokens.get(index).isAtTheEndOfSentence()) {
            lexicalTokens.get(index + 1).intensifyToken(intensification);
        }
    }
}
