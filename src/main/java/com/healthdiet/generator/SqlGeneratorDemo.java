package com.healthdiet.generator;

import com.healthdiet.entity.User;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class SqlGeneratorDemo {

    public static void main(String[] args) {
        // Create demo users similar to those in your database
        List<User> users = new ArrayList<>();

        // Normal user - weight loss goal
        User user1 = new User();
        user1.setId(2L);
        user1.setUsername("user1");
        user1.setRole(0);
        user1.setHeight(165.0);
        user1.setWeight(65.0);
        user1.setAge(28);
        user1.setGender(0); // Female
        user1.setTarget(-1); // LOSE_FAT
        user1.setActivityLevel(2); // LIGHT
        users.add(user1);

        // Normal user - muscle gain goal
        User user2 = new User();
        user2.setId(3L);
        user2.setUsername("user2");
        user2.setRole(0);
        user2.setHeight(180.0);
        user2.setWeight(75.0);
        user2.setAge(25);
        user2.setGender(1); // Male
        user2.setTarget(1); // GAIN_MUSCLE
        user2.setActivityLevel(3); // MODERATE
        users.add(user2);

        // Normal user - diabetes control
        User user3 = new User();
        user3.setId(4L);
        user3.setUsername("user3");
        user3.setRole(0);
        user3.setHeight(170.0);
        user3.setWeight(70.0);
        user3.setAge(45);
        user3.setGender(1); // Male
        user3.setTarget(2); // DIABETES_CONTROL
        user3.setActivityLevel(2); // LIGHT
        users.add(user3);

        // Normal user - hypertension control
        User user4 = new User();
        user4.setId(5L);
        user4.setUsername("user4");
        user4.setRole(0);
        user4.setHeight(175.0);
        user4.setWeight(80.0);
        user4.setAge(50);
        user4.setGender(0); // Female
        user4.setTarget(3); // HYPERTENSION_CONTROL
        user4.setActivityLevel(1); // SEDENTARY
        users.add(user4);

        // Normal user - maintain weight
        User user5 = new User();
        user5.setId(6L);
        user5.setUsername("user5");
        user5.setRole(0);
        user5.setHeight(168.0);
        user5.setWeight(60.0);
        user5.setAge(32);
        user5.setGender(0); // Female
        user5.setTarget(0); // MAINTAIN
        user5.setActivityLevel(3); // MODERATE
        users.add(user5);

        // Admin user (should be excluded)
        User admin = new User();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setRole(1); // Admin
        admin.setHeight(170.0);
        admin.setWeight(65.0);
        admin.setAge(30);
        admin.setGender(1);
        admin.setTarget(0);
        admin.setActivityLevel(2);
        users.add(admin);

        DietRecordGenerator generator = new DietRecordGenerator();

        // Generate clear records SQL
        String clearSQL = generator.generateClearRecordsSQL();
        System.out.println("=== CLEAR EXISTING RECORDS SQL ===");
        System.out.println(clearSQL);
        System.out.println();

        // Generate insert records SQL
        LocalDate endDate = LocalDate.now(); // Current date
        String insertSQL = generator.generateInsertRecordsSQL(users, endDate);
        System.out.println("=== INSERT NEW RECORDS SQL ===");
        System.out.println(insertSQL);

        // Save to file
        try {
            java.nio.file.Files.write(
                java.nio.file.Paths.get("generated_diet_records.sql"),
                (clearSQL + "\n\n" + insertSQL).getBytes()
            );
            System.out.println("SQL file saved to: generated_diet_records.sql");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}