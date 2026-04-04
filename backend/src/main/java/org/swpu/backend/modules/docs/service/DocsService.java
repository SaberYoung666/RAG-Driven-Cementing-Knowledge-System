package org.swpu.backend.modules.docs.service;

import org.springframework.web.multipart.MultipartFile;
import org.swpu.backend.common.api.PageResult;
import org.swpu.backend.modules.docs.dto.DocQuery;
import org.swpu.backend.modules.docs.dto.ProcessDocsRequest;
import org.swpu.backend.modules.docs.dto.RagDocStatusCallbackRequest;
import org.swpu.backend.modules.docs.vo.DocItem;
import org.swpu.backend.modules.docs.vo.DocProcessInfo;
import org.swpu.backend.modules.docs.vo.IngestResult;
import org.swpu.backend.modules.docs.vo.ProcessStartResult;

// 文档服务接口
public interface DocsService {
    PageResult<DocItem> listDocs(String bearerToken, DocQuery query);

    boolean deleteDoc(String bearerToken, String docId);

    IngestResult ingestFile(String bearerToken, MultipartFile file, boolean overwrite, String category);

    ProcessStartResult startProcessSingle(String bearerToken, String docId);

    ProcessStartResult startProcessBatch(String bearerToken, ProcessDocsRequest request);

    DocProcessInfo getProcessInfo(String bearerToken, String docId);

    void acceptRagStatusCallback(RagDocStatusCallbackRequest request);
}
