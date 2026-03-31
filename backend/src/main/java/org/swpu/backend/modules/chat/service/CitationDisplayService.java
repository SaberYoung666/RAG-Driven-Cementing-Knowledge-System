package org.swpu.backend.modules.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.swpu.backend.modules.chat.dto.ChatDto;
import org.swpu.backend.modules.docs.entity.DocEntity;
import org.swpu.backend.modules.docs.mapper.dao.DocMapper;

@Service
public class CitationDisplayService {
	private static final String DISK_SOURCE_PREFIX = "disk:";
	private static final Pattern CHUNK_BODY_PATTERN = Pattern.compile("p\\d+::c\\d+");

	private final DocMapper docMapper;

	public CitationDisplayService(DocMapper docMapper) {
		this.docMapper = docMapper;
	}

	public ChatDto.RagResp normalizeRagResp(ChatDto.RagResp resp) {
		if (resp == null || resp.citations() == null || resp.citations().isEmpty()) {
			return resp;
		}
		DocLookup lookup = loadDocLookupFromDto(resp.citations());
		List<ChatDto.Citation> citations = resp.citations().stream()
				.map(citation -> normalizeCitation(citation, lookup))
				.toList();
		return new ChatDto.RagResp(resp.answer(), resp.refused(), citations, resp.retrieved(), resp.debug(), resp.latencyMs());
	}

	public Map<String, Object> toCitationMap(String chunkId, String source, Integer page, String section, Double score) {
		DocLookup lookup = loadDocLookup(Set.of(), toNonEmptySet(source), toNonEmptySet(extractDocIdPrefix(chunkId)));
		String normalizedChunkId = normalizeChunkId(chunkId);
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("evidence_id", normalizedChunkId);
		row.put("score", score);
		row.put("chunk_id", normalizedChunkId);
		row.put("source", resolveDisplaySource(null, source, chunkId, lookup));
		row.put("page", page);
		row.put("section", section);
		return row;
	}

	private ChatDto.Citation normalizeCitation(ChatDto.Citation citation, DocLookup lookup) {
		if (citation == null) {
			return null;
		}
		String normalizedChunkId = normalizeChunkId(citation.chunkId());
		String evidenceId = normalizeEvidenceId(citation.evidenceId(), citation.chunkId(), normalizedChunkId);
		String source = resolveDisplaySource(citation.docId(), citation.source(), citation.chunkId(), lookup);
		return new ChatDto.Citation(
				evidenceId,
				citation.score(),
				citation.docId(),
				normalizedChunkId,
				source,
				citation.page(),
				citation.section()
		);
	}

	private String normalizeEvidenceId(String evidenceId, String rawChunkId, String normalizedChunkId) {
		if (!StringUtils.hasText(evidenceId) || Objects.equals(trimToNull(evidenceId), trimToNull(rawChunkId))) {
			return normalizedChunkId;
		}
		return evidenceId;
	}

	private String resolveDisplaySource(String docId, String source, String chunkId, DocLookup lookup) {
		DocEntity doc = lookup.find(docId, source, chunkId);
		if (doc != null && StringUtils.hasText(doc.getTitle())) {
			return doc.getTitle().trim();
		}
		return source;
	}

	private String normalizeChunkId(String chunkId) {
		String raw = trimToNull(chunkId);
		if (raw == null) {
			return null;
		}
		Matcher matcher = CHUNK_BODY_PATTERN.matcher(raw);
		if (!matcher.find()) {
			return raw;
		}
		return raw.substring(matcher.start()).trim();
	}

	private DocLookup loadDocLookupFromDto(List<ChatDto.Citation> citations) {
		Set<String> docIds = new LinkedHashSet<>();
		Set<String> sources = new LinkedHashSet<>();
		for (ChatDto.Citation citation : citations) {
			if (citation == null) {
				continue;
			}
			addIfHasText(docIds, citation.docId());
			addIfHasText(docIds, extractDocIdPrefix(citation.chunkId()));
			addIfHasText(sources, citation.source());
		}
		return loadDocLookup(docIds, sources);
	}

	private DocLookup loadDocLookup(Set<String> docIds, Set<String> sources) {
		if (docIds.isEmpty() && sources.isEmpty()) {
			return DocLookup.empty();
		}

		Map<String, DocEntity> docsByDocId = new LinkedHashMap<>();
		if (!docIds.isEmpty()) {
			QueryWrapper<DocEntity> docIdQuery = new QueryWrapper<>();
			docIdQuery.eq("deleted", false).in("doc_id", docIds);
			for (DocEntity doc : docMapper.selectList(docIdQuery)) {
				if (doc != null && StringUtils.hasText(doc.getDocId())) {
					docsByDocId.putIfAbsent(doc.getDocId().trim(), doc);
				}
			}
		}

		Set<String> sourceCandidates = toSourceCandidates(sources);
		if (!sourceCandidates.isEmpty()) {
			QueryWrapper<DocEntity> sourceQuery = new QueryWrapper<>();
			sourceQuery.eq("deleted", false).in("source", sourceCandidates);
			for (DocEntity doc : docMapper.selectList(sourceQuery)) {
				if (doc != null && StringUtils.hasText(doc.getDocId())) {
					docsByDocId.putIfAbsent(doc.getDocId().trim(), doc);
				}
			}
		}

		return DocLookup.from(docsByDocId.values());
	}

	private DocLookup loadDocLookup(Set<String> explicitDocIds, Set<String> sources, Set<String> derivedDocIds) {
		Set<String> docIds = new LinkedHashSet<>();
		docIds.addAll(explicitDocIds);
		docIds.addAll(derivedDocIds);
		return loadDocLookup(docIds, sources);
	}

	private Set<String> toSourceCandidates(Collection<String> sources) {
		Set<String> result = new LinkedHashSet<>();
		for (String source : sources) {
			String trimmed = trimToNull(source);
			if (trimmed == null) {
				continue;
			}
			result.add(trimmed);
			String normalized = normalizeSourceKey(trimmed);
			result.add(normalized);
			result.add(DISK_SOURCE_PREFIX + normalized);
		}
		return result;
	}

	private String extractDocIdPrefix(String chunkId) {
		String raw = trimToNull(chunkId);
		if (raw == null) {
			return null;
		}
		Matcher matcher = CHUNK_BODY_PATTERN.matcher(raw);
		if (!matcher.find() || matcher.start() == 0) {
			return null;
		}
		String prefix = raw.substring(0, matcher.start()).trim();
		while (prefix.endsWith(":") || prefix.endsWith("_") || prefix.endsWith("-")) {
			prefix = prefix.substring(0, prefix.length() - 1).trim();
		}
		return prefix.isEmpty() ? null : prefix;
	}

	private String normalizeSourceKey(String source) {
		String value = trimToNull(source);
		if (value == null) {
			return null;
		}
		return value.startsWith(DISK_SOURCE_PREFIX) ? value.substring(DISK_SOURCE_PREFIX.length()) : value;
	}

	private void addIfHasText(Set<String> values, String value) {
		String trimmed = trimToNull(value);
		if (trimmed != null) {
			values.add(trimmed);
		}
	}

	private Set<String> toNonEmptySet(String value) {
		String trimmed = trimToNull(value);
		return trimmed == null ? Set.of() : Set.of(trimmed);
	}

	private String trimToNull(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		return value.trim();
	}

	private static final class DocLookup {
		private final Map<String, DocEntity> byDocId;
		private final Map<String, DocEntity> bySource;

		private DocLookup(Map<String, DocEntity> byDocId, Map<String, DocEntity> bySource) {
			this.byDocId = byDocId;
			this.bySource = bySource;
		}

		private static DocLookup empty() {
			return new DocLookup(Map.of(), Map.of());
		}

		private static DocLookup from(Collection<DocEntity> docs) {
			Map<String, DocEntity> byDocId = new HashMap<>();
			Map<String, DocEntity> bySource = new HashMap<>();
			for (DocEntity doc : docs) {
				if (doc == null) {
					continue;
				}
				if (StringUtils.hasText(doc.getDocId())) {
					byDocId.putIfAbsent(doc.getDocId().trim(), doc);
				}
				String source = doc.getSource();
				if (StringUtils.hasText(source)) {
					String normalized = source.trim();
					bySource.putIfAbsent(normalized, doc);
					if (normalized.startsWith(DISK_SOURCE_PREFIX)) {
						bySource.putIfAbsent(normalized.substring(DISK_SOURCE_PREFIX.length()), doc);
					}
				}
			}
			return new DocLookup(Map.copyOf(byDocId), Map.copyOf(bySource));
		}

		private DocEntity find(String docId, String source, String chunkId) {
			String trimmedDocId = trim(docId);
			if (trimmedDocId != null && byDocId.containsKey(trimmedDocId)) {
				return byDocId.get(trimmedDocId);
			}

			String prefixDocId = trim(extractPrefix(chunkId));
			if (prefixDocId != null && byDocId.containsKey(prefixDocId)) {
				return byDocId.get(prefixDocId);
			}

			String normalizedSource = trim(normalizeSource(source));
			if (normalizedSource != null && bySource.containsKey(normalizedSource)) {
				return bySource.get(normalizedSource);
			}

			String rawSource = trim(source);
			if (rawSource != null && bySource.containsKey(rawSource)) {
				return bySource.get(rawSource);
			}
			return null;
		}

		private static String trim(String value) {
			return StringUtils.hasText(value) ? value.trim() : null;
		}

		private static String normalizeSource(String source) {
			String value = trim(source);
			if (value == null) {
				return null;
			}
			return value.startsWith(DISK_SOURCE_PREFIX) ? value.substring(DISK_SOURCE_PREFIX.length()) : value;
		}

		private static String extractPrefix(String chunkId) {
			String value = trim(chunkId);
			if (value == null) {
				return null;
			}
			Matcher matcher = CHUNK_BODY_PATTERN.matcher(value);
			if (!matcher.find() || matcher.start() == 0) {
				return null;
			}
			String prefix = value.substring(0, matcher.start()).trim();
			while (prefix.endsWith(":") || prefix.endsWith("_") || prefix.endsWith("-")) {
				prefix = prefix.substring(0, prefix.length() - 1).trim();
			}
			return prefix.isEmpty() ? null : prefix;
		}
	}
}
