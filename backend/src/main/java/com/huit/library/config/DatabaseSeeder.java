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
        seedData();
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

    private void seedData() {
        boolean hasBooks = entityManager.createQuery("SELECT count(b) FROM BookEntity b", Long.class).getSingleResult() > 0;
        
        // 1. Luôn đảm bảo các danh mục chuẩn tồn tại và dọn dẹp danh mục rác/thừa nếu có
        seedCategories();

        // 2. Nếu có sách nhưng có sách chưa được gán category (category_id IS NULL)
        if (hasBooks) {
            long unclassifiedCount = entityManager.createQuery("SELECT count(b) FROM BookEntity b WHERE b.category IS NULL", Long.class).getSingleResult();
            if (unclassifiedCount > 0) {
                System.out.println("Phát hiện " + unclassifiedCount + " cuốn sách chưa có thể loại. Đang tự động phân loại theo tiêu đề...");
                classifyBooksByTitleKeywords();
            }
        } else {
            System.out.println("Chưa có sách. Đọc từ file Books_Cleaned.csv...");
            parseAndSeedFromCSV();
        }
    }

    private void seedCategories() {
        // Xóa các category rác không thuộc danh sách chuẩn (nếu có)
        entityManager.createNativeQuery(
            "DELETE FROM categories WHERE name NOT IN ('Khoa học', 'Tình cảm', 'Hành động', 'Công nghệ thông tin', 'Kinh tế', 'Lịch sử', 'Kỹ năng sống', 'Văn học', 'Khác')"
        ).executeUpdate();

        // Thêm các category chuẩn nếu chưa tồn tại
        String insertQuery = "INSERT INTO categories (name, description, created_at, updated_at) " +
            "SELECT v.name, v.description, NOW(), NOW() " +
            "FROM (VALUES " +
            "  ('Khoa học', 'Sách khoa học tự nhiên và khám phá'), " +
            "  ('Tình cảm', 'Sách tình cảm, lãng mạn, tâm lý'), " +
            "  ('Hành động', 'Sách hành động, phiêu lưu, trinh thám'), " +
            "  ('Công nghệ thông tin', 'Sách công nghệ thông tin và lập trình'), " +
            "  ('Kinh tế', 'Sách kinh tế, tài chính, quản trị'), " +
            "  ('Lịch sử', 'Sách lịch sử, văn hóa xã hội'), " +
            "  ('Kỹ năng sống', 'Sách kỹ năng sống, phát triển bản thân'), " +
            "  ('Văn học', 'Sách văn học, tiểu thuyết, truyện ngắn'), " +
            "  ('Khác', 'Các thể loại khác') " +
            ") AS v(name, description) " +
            "WHERE NOT EXISTS (SELECT 1 FROM categories c WHERE c.name = v.name)";
        entityManager.createNativeQuery(insertQuery).executeUpdate();
    }

    private void classifyBooksByTitleKeywords() {
        String updateQuery = "WITH cats AS ( " +
            "  SELECT " +
            "    MAX(CASE WHEN name = 'Công nghệ thông tin' THEN category_id END) as id_it, " +
            "    MAX(CASE WHEN name = 'Khoa học' THEN category_id END) as id_science, " +
            "    MAX(CASE WHEN name = 'Kinh tế' THEN category_id END) as id_econ, " +
            "    MAX(CASE WHEN name = 'Lịch sử' THEN category_id END) as id_hist, " +
            "    MAX(CASE WHEN name = 'Tình cảm' THEN category_id END) as id_love, " +
            "    MAX(CASE WHEN name = 'Hành động' THEN category_id END) as id_action, " +
            "    MAX(CASE WHEN name = 'Kỹ năng sống' THEN category_id END) as id_life, " +
            "    MAX(CASE WHEN name = 'Văn học' THEN category_id END) as id_lit, " +
            "    MAX(CASE WHEN name = 'Khác' THEN category_id END) as id_other " +
            "  FROM categories " +
            ") " +
            "UPDATE books b " +
            "SET category_id = CASE " +
            "  WHEN b.title ~* '(computer|program|software|data|ai|web|java|python|c\\+\\+|code|tech|internet|network|linux|windows|database|algorithm|digital|developer|cyber|html|sql|system|pc|online|server)' THEN cats.id_it " +
            "  WHEN b.title ~* '(science|physics|chemistry|biology|nature|earth|space|universe|math|astronomy|medical|health|genetics|environment|planet|solar|galaxy|evolution|dinosaur|animal|wildlife|body|medicine|doctor|disease)' THEN cats.id_science " +
            "  WHEN b.title ~* '(business|econom|finance|market|money|invest|trade|management|accounting|banking|startup|corporat|wealth|dollar|stock|sales|brand|commerce|company|rich)' THEN cats.id_econ " +
            "  WHEN b.title ~* '(history|war|empire|ancient|century|revolution|chronicle|medieval|civilization|president|king|queen|dynasty|world war|rebellion|monarch|rome|greek|egypt)' THEN cats.id_hist " +
            "  WHEN b.title ~* '(love|romance|heart|passion|marry|marriage|relationship|kiss|darling|bride|groom|sweet|boyfriend|girlfriend|lover|wedding|dating|beloved|forever|hug)' THEN cats.id_love " +
            "  WHEN b.title ~* '(action|adventure|thriller|spy|danger|crime|kill|murder|mystery|detective|secret|shadow|deadly|assassin|gun|fight|chase|escape|rescue|hunt|revenge|bullet|fury|terror)' THEN cats.id_action " +
            "  WHEN b.title ~* '(life|success|habit|mind|skill|guide|heal|happy|peace|power|wisdom|think|art of|grow|change|friend|courage|inspire|daily|meditation|spirit|purpose|discipline)' THEN cats.id_life " +
            "  WHEN b.title ~* '(novel|story|poem|poetry|tale|classic|fiction|literature|legend|prose|drama|tales|fable|fantasy|myth|tragedy|magic|chronicles|essay|anthology)' THEN cats.id_lit " +
            "  ELSE cats.id_other " +
            "END " +
            "FROM cats " +
            "WHERE b.category_id IS NULL";
        int updated = entityManager.createNativeQuery(updateQuery).executeUpdate();
        System.out.println("Đã phân loại thành công " + updated + " cuốn sách theo thể loại.");
    }

    private String resolveCategoryFromTitle(String title) {
        if (title == null) return "Khác";
        String t = title.toLowerCase();
        if (t.matches(".*(computer|program|software|data|ai|web|java|python|c\\+\\+|code|tech|internet|network|linux|windows|database|algorithm|digital|developer|cyber|html|sql|system|pc|online|server).*")) {
            return "Công nghệ thông tin";
        }
        if (t.matches(".*(science|physics|chemistry|biology|nature|earth|space|universe|math|astronomy|medical|health|genetics|environment|planet|solar|galaxy|evolution|dinosaur|animal|wildlife|body|medicine|doctor|disease).*")) {
            return "Khoa học";
        }
        if (t.matches(".*(business|econom|finance|market|money|invest|trade|management|accounting|banking|startup|corporat|wealth|dollar|stock|sales|brand|commerce|company|rich).*")) {
            return "Kinh tế";
        }
        if (t.matches(".*(history|war|empire|ancient|century|revolution|chronicle|medieval|civilization|president|king|queen|dynasty|world war|rebellion|monarch|rome|greek|egypt).*")) {
            return "Lịch sử";
        }
        if (t.matches(".*(love|romance|heart|passion|marry|marriage|relationship|kiss|darling|bride|groom|sweet|boyfriend|girlfriend|lover|wedding|dating|beloved|forever|hug).*")) {
            return "Tình cảm";
        }
        if (t.matches(".*(action|adventure|thriller|spy|danger|crime|kill|murder|mystery|detective|secret|shadow|deadly|assassin|gun|fight|chase|escape|rescue|hunt|revenge|bullet|fury|terror).*")) {
            return "Hành động";
        }
        if (t.matches(".*(life|success|habit|mind|skill|guide|heal|happy|peace|power|wisdom|think|art of|grow|change|friend|courage|inspire|daily|meditation|spirit|purpose|discipline).*")) {
            return "Kỹ năng sống";
        }
        if (t.matches(".*(novel|story|poem|poetry|tale|classic|fiction|literature|legend|prose|drama|tales|fable|fantasy|myth|tragedy|magic|chronicles|essay|anthology).*")) {
            return "Văn học";
        }
        return "Khác";
    }

    private void parseAndSeedFromCSV() {
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(
                new org.springframework.core.io.ClassPathResource("data/Books_Cleaned.csv").getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            
            String line;
            boolean isFirstLine = true;
            
            java.util.Map<String, CategoryEntity> categoryMap = new java.util.HashMap<>();
            java.util.List<CategoryEntity> existingCats = entityManager.createQuery("SELECT c FROM CategoryEntity c", CategoryEntity.class).getResultList();
            for (CategoryEntity c : existingCats) {
                categoryMap.put(c.getName(), c);
            }
            
            int count = 0;
            while ((line = br.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }
                
                // Parse CSV by comma avoiding quotes
                String[] values = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                if (values.length < 8) continue;
                
                String isbn = values[0].replaceAll("^\"|\"$", "").trim();
                String title = values[1].replaceAll("^\"|\"$", "").trim();
                String author = values[2].replaceAll("^\"|\"$", "").trim();
                String yearStr = values[3].replaceAll("^\"|\"$", "").trim();
                String publisher = values[4].replaceAll("^\"|\"$", "").trim();
                String imgS = values[5].replaceAll("^\"|\"$", "").trim();
                String imgM = values[6].replaceAll("^\"|\"$", "").trim();
                String imgL = values[7].replaceAll("^\"|\"$", "").trim();
                
                String categoryName = resolveCategoryFromTitle(title);
                CategoryEntity category = categoryMap.get(categoryName);
                if (category == null) {
                    category = categoryMap.get("Khác");
                }
                
                // Create book
                BookEntity book = new BookEntity();
                book.setTitle(title.length() > 255 ? title.substring(0, 255) : title);
                book.setIsbn(isbn.length() > 255 ? isbn.substring(0, 255) : isbn);
                book.setAuthor(author.length() > 255 ? author.substring(0, 255) : author);
                book.setPublisher(publisher.length() > 255 ? publisher.substring(0, 255) : publisher);
                
                try {
                    book.setPublishYear(Integer.parseInt(yearStr));
                } catch (NumberFormatException e) {
                    book.setPublishYear(0);
                }
                
                book.setImageUrlS(imgS);
                book.setImageUrlM(imgM);
                book.setImageUrlL(imgL);
                book.setCategory(category);
                book.setIsDigital(false);
                
                entityManager.persist(book);
                
                count++;
                if (count % 1000 == 0) {
                    entityManager.flush();
                    entityManager.clear();
                    // Reload categories back to persistence context
                    categoryMap.clear();
                    java.util.List<CategoryEntity> reloadedCats = entityManager.createQuery("SELECT c FROM CategoryEntity c", CategoryEntity.class).getResultList();
                    for (CategoryEntity c : reloadedCats) {
                        categoryMap.put(c.getName(), c);
                    }
                }
                
                // For demo purposes, avoid reading 270k rows which takes too long during startup
                if (count >= 10000) {
                    break;
                }
            }
            
            entityManager.flush();
            System.out.println("Seeded " + count + " books and categories from CSV.");
        } catch (Exception e) {
            e.printStackTrace();
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
