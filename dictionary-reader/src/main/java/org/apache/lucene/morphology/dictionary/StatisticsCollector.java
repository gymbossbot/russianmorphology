/**
 * Copyright 2009 Alexander Kuznetsov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.lucene.morphology.dictionary;


import org.apache.lucene.morphology.Heuristic;
import org.apache.lucene.morphology.LetterDecoderEncoder;
import org.apache.lucene.morphology.MorphologyImpl;

import java.io.IOException;
import java.util.*;


//todo made refactoring this class
public class StatisticsCollector implements WordProcessor {
    private TreeMap<String, Set<Heuristic>> inverseIndex = new TreeMap<>();
    private Map<Set<Heuristic>, Integer> ruleInverseIndex = new HashMap<>();
    private List<Set<Heuristic>> rules = new ArrayList<>();
    private GrammarReader grammarReader;
    private LetterDecoderEncoder decoderEncoder;


    public StatisticsCollector(GrammarReader grammarReader, LetterDecoderEncoder decoderEncoder) {
        this.grammarReader = grammarReader;
        this.decoderEncoder = decoderEncoder;
    }

    public void process(WordCard wordCard) {
        cleanWordCard(wordCard);
        String normalStringMorph = wordCard.getWordsForms().get(0).getCode();

        for (FlexiaModel fm : wordCard.getWordsForms()) {
            Heuristic heuristic = createEvristic(wordCard.getBase(), wordCard.getCanonicalSuffix(), fm, normalStringMorph);
            String form = revertWord(fm.create(wordCard.getBase()));
            Set<Heuristic> suffixHeuristics = inverseIndex.computeIfAbsent(form, k -> new HashSet<>());
            suffixHeuristics.add(heuristic);
        }
    }

    private void cleanWordCard(WordCard wordCard) {
        wordCard.setBase(cleanString(wordCard.getBase()));
        wordCard.setCanonicalForm(cleanString(wordCard.getCanonicalForm()));
        wordCard.setCanonicalSuffix(cleanString(wordCard.getCanonicalSuffix()));
        List<FlexiaModel> models = wordCard.getWordsForms();
        for (FlexiaModel m : models) {
            m.setSuffix(cleanString(m.getSuffix()));
            m.setPrefix(cleanString(m.getPrefix()));
        }
    }


    public void saveHeuristic(String fileName) throws IOException {

        Map<Integer, Integer> dist = new TreeMap<>();
        Set<Heuristic> prevSet = null;
        int count = 0;
        for (String key : inverseIndex.keySet()) {
            Set<Heuristic> currentSet = inverseIndex.get(key);
            if (!currentSet.equals(prevSet)) {
                Integer d = dist.get(key.length());
                dist.put(key.length(), 1 + (d == null ? 0 : d));
                prevSet = currentSet;
                count++;
                if (!ruleInverseIndex.containsKey(currentSet)) {
                    ruleInverseIndex.put(currentSet, rules.size());
                    rules.add(currentSet);
                }
            }
        }
        System.out.println("Word with diffirent rules " + count);
        System.out.println("All ivers words " + inverseIndex.size());
        System.out.println(dist);
        System.out.println("diffirent rule count " + ruleInverseIndex.size());
        Heuristic[][] heuristics = new Heuristic[ruleInverseIndex.size()][];
        int index = 0;
        for (Set<Heuristic> hs : rules) {
            heuristics[index] = new Heuristic[hs.size()];
            int indexj = 0;
            for (Heuristic h : hs) {
                heuristics[index][indexj] = h;
                indexj++;
            }
            index++;
        }

        int[][] ints = new int[count][];
        short[] rulesId = new short[count];
        count = 0;
        prevSet = null;
        for (String key : inverseIndex.keySet()) {
            Set<Heuristic> currentSet = inverseIndex.get(key);
            if (!currentSet.equals(prevSet)) {
                int[] word = decoderEncoder.encodeToArray(key);
                ints[count] = word;
                rulesId[count] = (short) ruleInverseIndex.get(currentSet).intValue();
                count++;
                prevSet = currentSet;
            }
        }
        MorphologyImpl morphology = new MorphologyImpl(ints, rulesId, heuristics, grammarReader.getGrammarInfoAsArray());
        morphology.writeToFile(fileName);
    }

    private String revertWord(String s) {
        StringBuilder result = new StringBuilder();
        for (int i = 1; i <= s.length(); i++) {
            result.append(s.charAt(s.length() - i));
        }
        return result.toString();
    }


    private Heuristic createEvristic(String wordBase, String canonicalSuffix, FlexiaModel fm, String normalSuffixForm) {
        String form = fm.create(wordBase);
        String normalForm = wordBase + canonicalSuffix;
        Integer length = getCommonLength(form, normalForm);
        int actualSuffixLengh = form.length() - length;
        String actualNormalSuffix = normalForm.substring(length);
        Integer integer = grammarReader.getGrammarInverseIndex().get(fm.getCode());
        Integer nf = grammarReader.getGrammarInverseIndex().get(normalSuffixForm);
        return new Heuristic((byte) actualSuffixLengh, actualNormalSuffix, (short) integer.intValue(), (short) nf.intValue());
    }

    public static Integer getCommonLength(String s1, String s2) {
        int length = Math.min(s1.length(), s2.length());
        for (int i = 0; i < length; i++) {
            if (s1.charAt(i) != s2.charAt(i)) return i;
        }
        return length;
    }

    private String cleanString(String s) {
        return decoderEncoder.cleanString(s);
    }

}
