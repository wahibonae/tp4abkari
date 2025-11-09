package ma.emsi.abkari.tp4abkari.llm;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.content.retriever.WebSearchContentRetriever;
import dev.langchain4j.rag.query.router.DefaultQueryRouter;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LlmClient {
    // Clé pour l'API du LLM
    private final String key;

    // Rôle de l'assistant choisi par l'utilisateur
    private String systemRole;

    // Interface pour les interactions LLM
    private Assistant assistant;

    // Mémoire de l'assistant pour garder l'historique de la conversation
    private ChatMemory chatMemory;

    public LlmClient() throws URISyntaxException {
        // Récupère la clé secrète pour travailler avec l'API du LLM, mise dans une variable d'environnement
        // du système d'exploitation.
        this.key = System.getenv("GEMINI_KEY");
        String tavilyCle = System.getenv("TAVILY_KEY");

        if (key == null || key.isEmpty()) {
            System.err.println("❌ ERREUR: La clé GEMINI_KEY n'est pas définie!");
            throw new RuntimeException("La clé API GEMINI_KEY n'est pas configurée");
        }

        ChatModel chatModel = GoogleAiGeminiChatModel.builder()
                .apiKey(key)
                .modelName("gemini-2.5-flash")
                .temperature(0.3)
                .logRequestsAndResponses(true) // Technique Test 2
                .build();

        URL resourceUrl = LlmClient.class.getResource("/");
        Path resourcePath = Paths.get(resourceUrl.toURI());

        DocumentParser documentParser = new ApacheTikaDocumentParser();

        List<Document> documents = FileSystemDocumentLoader.loadDocuments(
                resourcePath,
                documentParser
        );

        DocumentSplitter splitter = DocumentSplitters.recursive(300, 50);

        // Split all documents into segments
        List<TextSegment> allSegments = new ArrayList<>();
        for (Document document : documents) {
            List<TextSegment> segments = splitter.split(document);
            allSegments.addAll(segments);
        }

        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

        List<Embedding> embeddings = embeddingModel.embedAll(allSegments).content();

        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
        embeddingStore.addAll(embeddings, allSegments);

        ContentRetriever pdfRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(3)
                .minScore(0.5)
                .build();

        WebSearchEngine webSearchEngine = TavilyWebSearchEngine.builder()
                .apiKey(tavilyCle)
                .build();

        ContentRetriever webRetriever = WebSearchContentRetriever.builder()
                .webSearchEngine(webSearchEngine)
                .build();


        QueryRouter queryRouter = new DefaultQueryRouter(
                Arrays.asList(pdfRetriever, webRetriever)
        );

        RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .queryRouter(queryRouter)
                .build();

        this.chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        this.assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .retrievalAugmentor(retrievalAugmentor)
                .chatMemory(chatMemory)
                .build();
    }

    public void setSystemRole(String systemRole) {
        this.chatMemory.clear();

        this.systemRole = systemRole;

        this.chatMemory.add(SystemMessage.from(systemRole));
    }

    public TokenStream envoyerRequete(String prompt) {
        return this.assistant.chat(prompt);
    }

    public void addReponse(AiMessage message) {
        this.chatMemory.add(message);
    }
}