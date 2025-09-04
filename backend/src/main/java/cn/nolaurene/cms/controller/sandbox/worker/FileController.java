package cn.nolaurene.cms.controller.sandbox.worker;

import cn.nolaurene.cms.common.sandbox.Response;
import cn.nolaurene.cms.common.sandbox.worker.req.file.*;
import cn.nolaurene.cms.common.sandbox.worker.resp.file.*;
import cn.nolaurene.cms.service.sandbox.worker.FileService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.concurrent.CompletableFuture;

/**
 * Date: 2025/5/19
 * Author: nolaurence
 * Description:  File operation API interfaces
 */
@Tag(name = "sandbox worker")
@RequestMapping("/file")
@RestController
public class FileController {

    @Resource
    private FileService fileService;

    /**
     * Read file content
     * @param request FileReadRequest
     * @return FileReadResult
     */
    @PostMapping("/read")
    public CompletableFuture<Response<FileReadResult>> readFile(@RequestBody FileReadRequest request) {
        boolean sudo = request.isSudo();
        return fileService.readFile(
                request.getFile(),
                request.getStartLine(),
                request.getEndLine(),
                sudo
        ).thenApply(Response::success);
    }


    /**
     * Write file content
     * @param request FileWriteRequest
     * @return FileWriteResult
     */
    @PostMapping("/write")
    public CompletableFuture<Response<FileWriteResult>> writeFile(@RequestBody FileWriteRequest request) {
        boolean sudo = request.isSudo();
        return fileService.writeFile(
                request.getFile(),
                request.getContent(),
                request.isAppend(),
                request.isLeadingNewline(),
                request.isTrailingNewline(),
                sudo
        ).thenApply(Response::success);
    }

    /**
     * Replace string in file
     * @param request FileReplaceRequest
     * @return FileReplaceResult
     */
    @PostMapping("/replace")
    public CompletableFuture<Response<FileReplaceResult>> replaceInFile(@RequestBody FileReplaceRequest request) {
        boolean sudo = request.isSudo();
        return fileService.replaceString(
                request.getFile(),
                request.getOldStr(),
                request.getNewStr(),
                sudo
        ).thenApply(Response::success);
    }

    /**
     * Search in file content
     * @param request FileSearchRequest
     * @return FileSearchResult
     */
    @PostMapping("/search")
    public CompletableFuture<Response<FileSearchResult>> searchInFile(@RequestBody FileSearchRequest request) {
        boolean sudo = request.isSudo();
        return fileService.searchInContent(
                request.getFile(),
                request.getRegex(),
                sudo
        ).thenApply(Response::success);
    }

    /**
     * Find files by name pattern
     * @param request FileFindRequest
     * @return FileFindResult
     */
    @PostMapping("/find")
    public CompletableFuture<Response<FileFindResult>> findInFile(@RequestBody FileFindRequest request) {
        return fileService.findFilesByName(
                request.getPath(),
                request.getGlob()
        ).thenApply(Response::success);
    }

}
