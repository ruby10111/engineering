package net.engineeringdigest.journalApp.Schedular;

import net.engineeringdigest.journalApp.cache.AppCache;
import net.engineeringdigest.journalApp.entity.JournalEntry;
import net.engineeringdigest.journalApp.entity.User;
import net.engineeringdigest.journalApp.enums.Sentiment;
import net.engineeringdigest.journalApp.model.SentimentData;
import net.engineeringdigest.journalApp.repository.UserRepositoryImpl;
import net.engineeringdigest.journalApp.service.EmailService;
import org.apache.commons.collections.map.HashedMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class UserScheduler {

    @Autowired
    private EmailService emailService;
    @Autowired
    private KafkaTemplate<String, SentimentData> kafkaTemplate;
    @Autowired
    private UserRepositoryImpl userRepository;

    @Autowired
    private AppCache appCache;



    @Scheduled(cron = "0 0 9 * * SUN")
    public void fetchUsersandsendmail() {
        List<User> users = userRepository.getUserForSA();
        for (User user : users) {
            List<JournalEntry> journalEntries = user.getJournalEntries();
            List<Sentiment> sentiments = journalEntries.stream().filter(x -> x.getDate().isAfter(LocalDateTime.now().minus(7, ChronoUnit.DAYS))).map(x -> x.getSentiment()).collect(Collectors.toList());
            Map<Sentiment, Integer> sentimentCounts = new HashMap<>();
            for (Sentiment sentiment : sentiments) {
                if (sentiment != null)
                    sentimentCounts.put(sentiment, sentimentCounts.getOrDefault(sentiment, 0) + 1);
            }
            Sentiment mostFrequentSentiment = null;
            int Maxcount = 0;
            for (Map.Entry<Sentiment, Integer> entry : sentimentCounts.entrySet()) {
                if (entry.getValue() > Maxcount) {
                    Maxcount = entry.getValue();
                    mostFrequentSentiment = entry.getKey();
                }
            }
            if (mostFrequentSentiment != null) {
                SentimentData sentimentData = SentimentData.builder().email(user.getEmail()).sentiment("Sentiment for last 7 days " + mostFrequentSentiment).build();

                try {
                    kafkaTemplate.send("weekly-sentiments", sentimentData.getEmail(), sentimentData);
                } catch (Exception e) {
                    emailService.sendemail(sentimentData.getEmail(), "Sentiment for previous week", sentimentData.getSentiment());
                }

            }
        }
    }

    @Scheduled(cron = "0 0/1 * ? * *")
    public void clearappcache(){
    appCache.init();
    }
}
