package br.car.dsp_geo_file.theme;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Loads the download theme catalogue once per run.
 */
@Slf4j
@Service
@EnableConfigurationProperties(DownloadThemesProperties.class)
public class DownloadThemesService {

    private final DownloadThemesProperties properties;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader = new DefaultResourceLoader();

    private volatile DownloadThemesDocument cachedDocument;

    public DownloadThemesService(DownloadThemesProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public List<DownloadThemeConfig> getEnabledThemes() {
        return getDocument().themes().stream()
                .filter(DownloadThemeConfig::enabled)
                .toList();
    }

    private DownloadThemesDocument getDocument() {
        DownloadThemesDocument current = cachedDocument;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (cachedDocument == null) {
                cachedDocument = loadDocument(properties.getThemesFile());
            }
            return cachedDocument;
        }
    }

    private DownloadThemesDocument loadDocument(String location) {
        log.info("Loading download themes from {}", location);
        try (InputStream input = openStream(location)) {
            DownloadThemesDocument document =
                    objectMapper.readValue(input, DownloadThemesDocument.class);
            if (document.themes() == null || document.themes().isEmpty()) {
                throw new IOException("Download themes config has no themes");
            }
            return document;
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to load download themes from " + location, ex);
        }
    }

    private InputStream openStream(String location) throws IOException {
        if (location.startsWith("classpath:") || location.startsWith("file:")) {
            Resource resource = resourceLoader.getResource(location);
            if (!resource.exists()) {
                throw new IOException("Download themes config not found: " + location);
            }
            return resource.getInputStream();
        }

        Path path = Path.of(location);
        if (!Files.exists(path)) {
            throw new IOException("Download themes config not found: " + path.toAbsolutePath());
        }
        return Files.newInputStream(path);
    }
}
