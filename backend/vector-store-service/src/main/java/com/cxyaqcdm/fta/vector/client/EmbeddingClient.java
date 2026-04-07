package com.cxyaqcdm.fta.vector.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

@FeignClient(name = "embedding-service", url = "${embedding.service.url:http://localhost:8001}")
public interface EmbeddingClient {

    @PostMapping("/api/v1/embedding/embed")
    List<double[]> embedTexts(@RequestBody EmbeddingRequest request);

    @PostMapping("/api/v1/embedding/search")
    List<SearchResult> searchSimilar(@RequestBody SearchRequest request);

    class EmbeddingRequest {
        public List<String> texts;
        public String model;
        
        public EmbeddingRequest() {}
        
        public EmbeddingRequest(List<String> texts, String model) {
            this.texts = texts;
            this.model = model;
        }
    }

    class SearchRequest {
        public String query;
        public List<String> documentTexts;
        public int topK;
        public String model;
        
        public SearchRequest() {}
        
        public SearchRequest(String query, List<String> documentTexts, int topK, String model) {
            this.query = query;
            this.documentTexts = documentTexts;
            this.topK = topK;
            this.model = model;
        }
    }

    class SearchResult {
        public int index;
        public String text;
        public double similarityScore;
        
        public SearchResult() {}
        
        public SearchResult(int index, String text, double similarityScore) {
            this.index = index;
            this.text = text;
            this.similarityScore = similarityScore;
        }
    }
}
