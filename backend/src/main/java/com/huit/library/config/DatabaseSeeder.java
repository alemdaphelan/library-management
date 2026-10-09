package com.huit.library.config;

import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.catalog.entity.BookCopyEntity;
import com.huit.library.modules.catalog.entity.CategoryEntity;
import com.huit.library.modules.inventory.entity.SupplierEntity;
import jakarta.persistence.EntityManager;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;

    public DatabaseSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder, EntityManager entityManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        seedUsers();
        seedCategories();
        seedBooksAndCopies();
        seedSuppliers();
    }

    private void seedUsers() {
        if (userRepository.count() == 0) {
            UserEntity admin = new UserEntity();
            admin.setUserId(UUID.randomUUID());
            admin.setFullName("System Admin");
            admin.setEmail("admin@huit.edu.vn");
            admin.setPasswordHash(passwordEncoder.encode("admin123"));
            admin.setRoleId("ADMIN");
            admin.setUserType("STAFF");
            admin.setMssv("STAFF001");
            userRepository.save(admin);

            UserEntity student = new UserEntity();
            student.setUserId(UUID.randomUUID());
            student.setFullName("Nguyen Van A");
            student.setEmail("nva@student.huit.edu.vn");
            student.setPasswordHash(passwordEncoder.encode("student123"));
            student.setRoleId("STUDENT");
            student.setUserType("STUDENT");
            student.setMssv("2001210001");
            student.setDepartment("CNTT");
            userRepository.save(student);
            
            System.out.println("Seeded admin & student user.");
        }
    }

    private void seedCategories() {
        if (entityManager.createQuery("SELECT count(c) FROM CategoryEntity c", Long.class).getSingleResult() == 0) {
            String[] categoryNames = {"Công nghệ thông tin", "Kinh tế", "Ngoại ngữ", "Khoa học cơ bản"};
            for (String name : categoryNames) {
                CategoryEntity cat = new CategoryEntity();
                cat.setName(name);
                cat.setDescription("Sách thuộc lĩnh vực " + name);
                entityManager.persist(cat);
            }
            System.out.println("Seeded categories.");
        }
    }

    private void seedBooksAndCopies() {
        if (entityManager.createQuery("SELECT count(b) FROM BookEntity b", Long.class).getSingleResult() == 0) {
            // Book 1
            BookEntity book1 = new BookEntity();
            book1.setTitle("Lập trình Java cơ bản");
            book1.setIsbn("978-604-1-12345-6");
            book1.setDdcCallNumber("005.133");
            book1.setPublishYear(2023);
            book1.setLanguage("vi");
            book1.setLabelColor("Xanh lá");
            book1.setDefaultPrice(new BigDecimal("150000"));
            book1.setIsDigital(false);
            entityManager.persist(book1);
            
            // Copies for Book 1
            for(int i = 1; i <= 3; i++) {
                BookCopyEntity copy = new BookCopyEntity();
                copy.setBarcode("JAVA-00" + i);
                copy.setBookId(book1.getBookId());
                copy.setStatus("AVAILABLE");
                entityManager.persist(copy);
            }

            // Book 2
            BookEntity book2 = new BookEntity();
            book2.setTitle("Spring Boot in Action");
            book2.setIsbn("978-1-61729-254-5");
            book2.setDdcCallNumber("005.133");
            book2.setPublishYear(2021);
            book2.setLanguage("en");
            book2.setLabelColor("Đỏ");
            book2.setDefaultPrice(new BigDecimal("350000"));
            book2.setIsDigital(false);
            entityManager.persist(book2);
            
            // Copies for Book 2
            for(int i = 1; i <= 2; i++) {
                BookCopyEntity copy = new BookCopyEntity();
                copy.setBarcode("SPRING-00" + i);
                copy.setBookId(book2.getBookId());
                copy.setStatus("AVAILABLE");
                entityManager.persist(copy);
            }

            System.out.println("Seeded books & copies (with locations).");
        }
    }

    private void seedSuppliers() {
        if (entityManager.createQuery("SELECT count(s) FROM SupplierEntity s", Long.class).getSingleResult() == 0) {
            SupplierEntity sup1 = new SupplierEntity();
            sup1.setName("Nhà xuất bản Trẻ");
            sup1.setContactInfo("0123456789 - info@nxbtre.vn");
            entityManager.persist(sup1);

            SupplierEntity sup2 = new SupplierEntity();
            sup2.setName("Fahasa");
            sup2.setContactInfo("19001234 - contact@fahasa.com");
            entityManager.persist(sup2);
            
            System.out.println("Seeded suppliers.");
        }
    }
}
