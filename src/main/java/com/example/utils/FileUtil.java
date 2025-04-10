package com.example.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class FileUtil {
    private static final Logger logger = LoggerFactory.getLogger(FileUtil.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String REQUEST_TEMPLATES_DIR = "requestTemplates/"; // Thư mục chứa các template

    public static List<Map<String, Object>> getTestCases(String fileName) throws IOException {
        logger.info("Reading test cases from file: {}", fileName);
        try (InputStream inputStream = FileUtil.class.getClassLoader().getResourceAsStream(fileName)) {
            if (inputStream == null) {
                logger.error("Cannot find file: {}", fileName);
                throw new IllegalArgumentException("Cannot find file: " + fileName);
            }
            List<Map<String, Object>> testCases = mapper.readValue(inputStream, new TypeReference<List<Map<String, Object>>>() {});
            logger.info("Successfully read {} test cases from file: {}", testCases.size(), fileName);
            return testCases;
        } catch (IOException e) {
            logger.error("Failed to parse JSON from file: {}", fileName, e);
            throw e;
        }
    }

    public static Object getRequestTemplate(String key) throws IOException {
        // Xây dựng đường dẫn đến file JSON: requestTemplates/<key>.json
        String filePath = REQUEST_TEMPLATES_DIR + key + ".json";
        logger.info("Reading request template from file: {}", filePath);

        try (InputStream inputStream = FileUtil.class.getClassLoader().getResourceAsStream(filePath)) {
            if (inputStream == null) {
                logger.error("Cannot find template file: {}", filePath);
                throw new IllegalArgumentException("Cannot find template file: " + filePath);
            }
            // Đọc trực tiếp file JSON thành Object
            Object template = mapper.readValue(inputStream, Object.class);
            logger.info("Successfully read template from file: {}", filePath);
            return template;
        } catch (IOException e) {
            logger.error("Failed to parse JSON from file: {}", filePath, e);
            throw e;
        }
    }
}