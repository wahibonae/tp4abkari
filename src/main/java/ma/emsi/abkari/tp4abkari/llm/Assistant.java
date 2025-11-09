package ma.emsi.abkari.tp4abkari.llm;

import dev.langchain4j.service.TokenStream;

public interface Assistant {
    TokenStream chat(String prompt);
}