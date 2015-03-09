Yet Another Language DEtectoR

The goal of Yalder is to support fast, accurate language detection across the most popular (based on Wikipedia pages) 50 or so languages.

The approach I'm using is to use LLR (Log-Likelihood Ratio) and DF (document frequency) scores for 1-4 ngrams to pick the "best" set of these that is of a reasonable size. This is typically 500-2000, depending on the tradeoff of speed versus accuracy.

For each such set of the "best" ngrams extract from training data, I construct a feature vector where the weight of each ngram is always one (no term frequency information). I then normalize the vector to have a length of one. This feature vector is the model for the language.

When given an arbitrary chunk of text to classify, I build a similar feature vector based on all ngrams from any of the active models that occurs in the target text. The dot product of this vector with each model vector is that model's score.

Current results are promising. It takes roughly 2.3 seconds to classify all 17000 chunks of text (1000 lines/language * 17 languages) that were used in Mike McCandless's speed tests, which means it's faster than language-detection (and much faster than Tika). It's still much slower than Google's CLD (Chrome Language Detector), which I believe is written in C and has Python bindings that Mike used to test.

Accuracy is 99.4% across all 17 languages. I can improve this by using special pair-wise language models (e.g. 'pt' and 'es', which only contain ngrams useful for disambiguating between those two languages). This impacts performance, but can be a worth-while tradeoff.

In general the more ngrams for a model the higher the accuracy, especially for short snippets of text, as it's more likely that the snippet will contain a reasonable number of the model's ngrams. This comes at the expense of speed, and to a lesser extent the size of the model data.

=Implementation Details=

TBD
