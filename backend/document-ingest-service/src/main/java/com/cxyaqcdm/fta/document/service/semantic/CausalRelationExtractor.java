package com.cxyaqcdm.fta.document.service.semantic;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CausalRelationExtractor {

    private static final List<CausalPattern> CAUSAL_PATTERNS = new ArrayList<>();

    static {
        CAUSAL_PATTERNS.add(new CausalPattern(
                "(.+)由于(.+)导致(.+)",
                "由于", "导致", 0.9
        ));
        CAUSAL_PATTERNS.add(new CausalPattern(
                "(.+)因为(.+)引起(.+)",
                "因为", "引起", 0.9
        ));
        CAUSAL_PATTERNS.add(new CausalPattern(
                "(.+)因(.+)而(.+)",
                "因", "而", 0.85
        ));
        CAUSAL_PATTERNS.add(new CausalPattern(
                "(.+)假如(.+)就(.+)",
                "假如", "就", 0.8
        ));
        CAUSAL_PATTERNS.add(new CausalPattern(
                "(.+)一旦(.+)就(.+)",
                "一旦", "就", 0.8
        ));
        CAUSAL_PATTERNS.add(new CausalPattern(
                "(.+)如果(.+)则(.+)",
                "如果", "则", 0.85
        ));
        CAUSAL_PATTERNS.add(new CausalPattern(
                "(.+)是由于(.+)造成的",
                "是由于", "造成的", 0.9
        ));
        CAUSAL_PATTERNS.add(new CausalPattern(
                "(.+)起因于(.+)",
                "起因于", null, 0.85
        ));
        CAUSAL_PATTERNS.add(new CausalPattern(
                "(.+)根源是(.+)",
                "根源是", null, 0.85
        ));
        CAUSAL_PATTERNS.add(new CausalPattern(
                "(.+)导致(.+)",
                "导致", null, 0.75
        ));
        CAUSAL_PATTERNS.add(new CausalPattern(
                "(.+)引起(.+)",
                "引起", null, 0.75
        ));
        CAUSAL_PATTERNS.add(new CausalPattern(
                "(.+)造成(.+)",
                "造成", null, 0.75
        ));
        CAUSAL_PATTERNS.add(new CausalPattern(
                "(.+)使得(.+)",
                "使得", null, 0.7
        ));
        CAUSAL_PATTERNS.add(new CausalPattern(
                "(.+)从而(.+)",
                "从而", null, 0.7
        ));
        CAUSAL_PATTERNS.add(new CausalPattern(
                "(.+)，(.+)诱发(.+)",
                "，", "诱发", 0.85
        ));
        CAUSAL_PATTERNS.add(new CausalPattern(
                "(.+)，(.+)促使(.+)",
                "，", "促使", 0.8
        ));
        CAUSAL_PATTERNS.add(new CausalPattern(
                "(.+)，(.+)引发(.+)",
                "，", "引发", 0.8
        ));
        CAUSAL_PATTERNS.add(new CausalPattern(
                "(.+)[，,](.+)导致(.+)故障",
                "，", "导致", 0.9
        ));
        CAUSAL_PATTERNS.add(new CausalPattern(
                "(.+)[，,](.+)造成(.+)失效",
                "，", "造成", 0.9
        ));
    }

    public List<SemanticParagraph.CausalTriple> extractCausalRelations(String text) {
        List<SemanticParagraph.CausalTriple> triples = new ArrayList<>();
        Set<String> processedSignals = new HashSet<>();

        for (CausalPattern pattern : CAUSAL_PATTERNS) {
            Matcher matcher = pattern.regex.matcher(text);
            while (matcher.find()) {
                String signalPhrase = findSignalPhrase(text, matcher, pattern);
                if (signalPhrase != null && !processedSignals.contains(signalPhrase)) {
                    processedSignals.add(signalPhrase);

                    String cause = matcher.group(1).trim();
                    String effect = pattern.secondMarker != null ?
                            matcher.group(3 != matcher.groupCount() ? 3 : 2).trim() :
                            matcher.group(2).trim();

                    if (cause.length() > 2 && effect.length() > 2) {
                        SemanticParagraph.CausalTriple triple = SemanticParagraph.CausalTriple.builder()
                                .cause(cause)
                                .effect(effect)
                                .relationType(determineRelationType(pattern))
                                .signalPhrase(signalPhrase)
                                .confidence(pattern.confidence)
                                .build();
                        triples.add(triple);
                    }
                }
            }
        }

        triples.sort(Comparator.comparingInt(t -> text.indexOf(t.getSignalPhrase())));
        return triples;
    }

    private String findSignalPhrase(String text, Matcher matcher, CausalPattern pattern) {
        int start = Math.max(0, matcher.start() - 5);
        int end = Math.min(text.length(), matcher.end() + 5);
        String snippet = text.substring(start, end);

        if (pattern.secondMarker != null) {
            int firstIdx = snippet.indexOf(pattern.firstMarker);
            if (firstIdx != -1) {
                return pattern.firstMarker;
            }
        }

        if (pattern.firstMarker != null && snippet.contains(pattern.firstMarker)) {
            return pattern.firstMarker;
        }

        return matcher.group();
    }

    private String determineRelationType(CausalPattern pattern) {
        if (pattern.firstMarker.contains("由于") || pattern.firstMarker.contains("因为")) {
            return "causes";
        } else if (pattern.firstMarker.contains("如果") || pattern.firstMarker.contains("假如")) {
            return "conditional";
        } else if (pattern.firstMarker.contains("一旦")) {
            return "trigger";
        } else if (pattern.firstMarker.contains("导致") || pattern.firstMarker.contains("引起")) {
            return "results_in";
        }
        return "related_to";
    }

    public Map<String, List<SemanticParagraph.CausalTriple>> groupByRelationType(
            List<SemanticParagraph.CausalTriple> triples) {
        return triples.stream()
                .collect(Collectors.groupingBy(SemanticParagraph.CausalTriple::getRelationType));
    }

    public List<SemanticParagraph.CausalTriple> filterHighConfidence(
            List<SemanticParagraph.CausalTriple> triples, double threshold) {
        return triples.stream()
                .filter(t -> t.getConfidence() >= threshold)
                .collect(Collectors.toList());
    }

    private static class CausalPattern {
        Pattern regex;
        String firstMarker;
        String secondMarker;
        double confidence;

        CausalPattern(String regex, String firstMarker, String secondMarker, double confidence) {
            this.regex = Pattern.compile(regex);
            this.firstMarker = firstMarker;
            this.secondMarker = secondMarker;
            this.confidence = confidence;
        }
    }
}