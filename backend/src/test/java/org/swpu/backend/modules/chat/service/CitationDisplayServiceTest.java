package org.swpu.backend.modules.chat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.swpu.backend.modules.chat.dto.ChatDto;
import org.swpu.backend.modules.docs.entity.DocEntity;
import org.swpu.backend.modules.docs.mapper.dao.DocMapper;

class CitationDisplayServiceTest {

	@Test
	void shouldNormalizeRagCitationFields() {
		DocMapper docMapper = Mockito.mock(DocMapper.class);
		CitationDisplayService service = new CitationDisplayService(docMapper);
		DocEntity doc = new DocEntity();
		doc.setDocId("encrypted-doc-id");
		doc.setTitle("真实文件名.pdf");
		doc.setSource("disk:encrypted-file.pdf");
		when(docMapper.selectList(any())).thenReturn(List.of(doc), List.of(doc));

		ChatDto.Citation citation = new ChatDto.Citation(
				"encrypted-doc-id::p12::c34",
				0.91,
				"encrypted-doc-id",
				"encrypted-doc-id::p12::c34",
				"encrypted-file.pdf",
				12,
				"5.3"
		);
		ChatDto.RagResp resp = new ChatDto.RagResp("answer", false, List.of(citation), List.of(), null, 9);

		ChatDto.RagResp normalized = service.normalizeRagResp(resp);

		assertEquals("p12::c34", normalized.citations().getFirst().evidenceId());
		assertEquals("p12::c34", normalized.citations().getFirst().chunkId());
		assertEquals("真实文件名.pdf", normalized.citations().getFirst().source());
	}

	@Test
	void shouldNormalizePersistedCitationFields() {
		DocMapper docMapper = Mockito.mock(DocMapper.class);
		CitationDisplayService service = new CitationDisplayService(docMapper);
		DocEntity doc = new DocEntity();
		doc.setDocId("encrypted-doc-id");
		doc.setTitle("固井资料.docx");
		doc.setSource("disk:encrypted-file.docx");
		when(docMapper.selectList(any())).thenReturn(List.of(doc), List.of(doc));

		Map<String, Object> result = service.toCitationMap(
				"encrypted-doc-id::p1::c2",
				"encrypted-file.docx",
				1,
				"section",
				0.87
		);

		assertEquals("p1::c2", result.get("evidence_id"));
		assertEquals("p1::c2", result.get("chunk_id"));
		assertEquals("固井资料.docx", result.get("source"));
		assertEquals(0.87, result.get("score"));
		assertNull(result.get("missing"));
	}
}
