Yet Another Language DEtectoR
=============================

Overview
--------
Yalder is a Java-based language detector. You give it a CharacterSequence and a set of language "models", and it returns a score and a confidence value for each model, where a higher score means that it's more likely the text provided is written in that model's language.

Goal
----
The goal of Yalder is to support fast, accurate language detection across the most popular (based on Wikipedia pages) 50 or so languages.

Design
------
The approach I'm using is to use LLR (Log-Likelihood Ratio) and DF (document frequency) scores for 1-4 ngrams to pick the "best" set of these that is of a reasonable size. This is typically 500-2000, depending on the tradeoff of speed versus accuracy.

For each such set of the "best" ngrams extract from training data, I construct a feature vector where the weight of each ngram is based on the length; longer ngrams have higher weight (so no term frequency information). I then normalize the vector to have a length of one. This feature vector is the model for that specific language.

When given an arbitrary chunk of text to classify, I build a similar feature vector based on all ngrams from any of the active models that occurs in the target text. The dot product of this vector with each model vector is that model's score. Note that because of the use of LLR, the model's score is really a prediction of probability that the text uses the model's language versus any of the other languages in the corpus.

Current results are promising. It takes roughly 2.3 seconds to classify all 17000 chunks of text (1000 lines/language * 17 languages) that were used in Mike McCandless's [language detection speed test](http://blog.mikemccandless.com/2011/10/accuracy-and-performance-of-googles.html), which means it's faster than language-detection (and much faster than Tika). It's still much slower than Google's [Compact Language Detector](https://code.google.com/p/cld2/), which is written in C, but has a [Python binding](http://code.google.com/p/chromium-compact-language-detector) that Mike used to test.

Accuracy is 99.4% across all 17 languages. I can improve this by using special pair-wise language models (e.g. 'pt' and 'es', which only contain ngrams useful for disambiguating between those two languages). This impacts performance, but can be a worth-while tradeoff.

In general the more ngrams for a model the higher the accuracy, especially for short snippets of text, as it's more likely that the snippet will contain a reasonable number of the model's ngrams. This comes at the expense of speed, and to a lesser extent the size of the model data.

Implementation Details
----------------------

TBD
