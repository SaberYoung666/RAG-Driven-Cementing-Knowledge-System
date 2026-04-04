package org.swpu.backend.modules.docs.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.swpu.backend.modules.docs.vo.DocProcessStatusSnapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
public class DocProcessStatusStore {
	private static final TypeReference<Map<String, DocProcessStatusSnapshot>> STORE_TYPE = new TypeReference<>() {
	};

	private final Path storePath;
	private final ObjectMapper objectMapper;
	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	private Map<String, DocProcessStatusSnapshot> snapshots = new LinkedHashMap<>();

	public DocProcessStatusStore(
			ObjectMapper objectMapper,
			@Value("${docs.process-status-store-path:data/doc_process_status.json}") String storePath) {
		this.objectMapper = objectMapper;
		this.storePath = Paths.get(storePath).toAbsolutePath().normalize();
		load();
	}

	public DocProcessStatusSnapshot get(String docId) {
		lock.readLock().lock();
		try {
			return snapshots.get(docId);
		} finally {
			lock.readLock().unlock();
		}
	}

	public DocProcessStatusSnapshot upsert(DocProcessStatusSnapshot incoming) {
		if (incoming == null || !StringUtils.hasText(incoming.docId())) {
			return null;
		}
		lock.writeLock().lock();
		try {
			DocProcessStatusSnapshot existing = snapshots.get(incoming.docId());
			DocProcessStatusSnapshot resolved = resolve(existing, incoming);
			snapshots.put(incoming.docId(), resolved);
			save();
			return resolved;
		} finally {
			lock.writeLock().unlock();
		}
	}

	private void load() {
		lock.writeLock().lock();
		try {
			Files.createDirectories(storePath.getParent());
			if (!Files.exists(storePath)) {
				snapshots = new LinkedHashMap<>();
				return;
			}
			Map<String, DocProcessStatusSnapshot> loaded = objectMapper.readValue(storePath.toFile(), STORE_TYPE);
			snapshots = loaded == null ? new LinkedHashMap<>() : new LinkedHashMap<>(loaded);
		} catch (Exception ex) {
			snapshots = new LinkedHashMap<>();
		} finally {
			lock.writeLock().unlock();
		}
	}

	private void save() {
		try {
			Files.createDirectories(storePath.getParent());
			objectMapper.writerWithDefaultPrettyPrinter().writeValue(storePath.toFile(), snapshots);
		} catch (IOException ex) {
			throw new IllegalStateException("persist doc process status failed", ex);
		}
	}

	private DocProcessStatusSnapshot resolve(DocProcessStatusSnapshot existing, DocProcessStatusSnapshot incoming) {
		if (existing == null) {
			return incoming;
		}
		if (isTerminal(existing.status()) && !isTerminal(incoming.status())) {
			return existing;
		}
		if (isTerminal(existing.status()) && isTerminal(incoming.status())) {
			Instant existingAt = parseInstant(existing.updatedAt());
			Instant incomingAt = parseInstant(incoming.updatedAt());
			if (existingAt != null && incomingAt != null && existingAt.isAfter(incomingAt)) {
				return existing;
			}
		}
		return incoming;
	}

	private boolean isTerminal(String status) {
		if (!StringUtils.hasText(status)) {
			return false;
		}
		String normalized = status.trim().toLowerCase();
		return "done".equals(normalized) || "success".equals(normalized) || "ready".equals(normalized)
				|| "failed".equals(normalized) || "error".equals(normalized);
	}

	private Instant parseInstant(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		try {
			return Instant.parse(value.trim());
		} catch (Exception ex) {
			return null;
		}
	}
}
