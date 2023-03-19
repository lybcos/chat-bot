package com.example;

import cn.hutool.core.text.UnicodeUtil;
import com.alibaba.fastjson.JSON;
import com.example.ai.Answer;
import com.example.ai.Choices;
import com.example.properties.OpenAi;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

@SpringBootApplication
@RestController
@CrossOrigin
@ConfigurationPropertiesScan(basePackages = "com.example.properties")
public class ChatbotApplication {
    @Autowired
    private OpenAi openAi;
    @Autowired
    private WebClient webClient;

    public static void main(String[] args) {
        SpringApplication.run(ChatbotApplication.class, args);
    }

    @Value("${openai.secretKey}")
    private String secretKey;

    @GetMapping(value = "/request")
    public Flux<String> request(HttpServletResponse response, @RequestParam String question) {
        return submit(response, question);
    }

    @PostMapping(value = "/request")
    public Flux<String> request(HttpServletResponse response, @RequestBody OpenAi openAi) {
        return submit(response, openAi.getPrompt());
    }

    private Flux<String> submit(HttpServletResponse response, @RequestParam String question) {
        response.setContentType("text/event-stream;charset=utf-8");
        openAi.setPrompt(question);
        WebClient.ResponseSpec authorization = webClient.post()
                .uri("https://api.openai.com/v1/completions")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + secretKey)
                .bodyValue(JSON.toJSONString(openAi))
                .retrieve();
        Flux<String> stringFlux = authorization.bodyToFlux(String.class);
        return stringFlux.mapNotNull(string -> {
            if (string.contains("DONE")) {
                return "Answer Done!";
            }
            Answer answer = JSON.parseObject(string, Answer.class);
            StringBuilder stringBuilder = new StringBuilder();
            List<Choices> choices = answer.getChoices();
            choices.forEach(choice -> {
                stringBuilder.append(UnicodeUtil.toString(choice.getText()));
            });
            StringBuilder s = new StringBuilder(stringBuilder.toString());
            if (s.toString().contains("\n")) {
                s = new StringBuilder("<br/>");
            }
            return s.toString().replace(" ", "&ensp;");
        }).cache();
    }

}
