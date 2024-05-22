package com.vk.codeanalysis.core.file_tracker;

import com.vk.codeanalysis.public_interface.distributor.DistributorServiceV0;
import com.vk.codeanalysis.public_interface.dto.SolutionPutRequest;
import com.vk.codeanalysis.public_interface.file_tracker.FileTrackerService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileTrackerServiceImpl implements FileTrackerService {

    private final ExecutorService trackerExecutor;
    private final DistributorServiceV0 distributorService;

    @Value("${file-tracker.path}")
    private String trackPath;

    @PostConstruct
    @Override
    public void trackSolutions() {
        Path rootPath = Path.of(trackPath);
        trackerExecutor.execute(() -> {
            try {
                Map<Long, SolutionPutRequest> latestSolutions = new HashMap<>();
                Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {

                        SolutionPutRequest solutionPutRequest = SolutionPathParser.parseSolutionPath(file.toString());
                        if (solutionPutRequest == null) {
                            return FileVisitResult.CONTINUE;
                        }
                        Long userId = solutionPutRequest.userId();
                        latestSolutions.merge(userId, solutionPutRequest, (oldSolution, newSolution) ->
                                oldSolution.solutionId() < newSolution.solutionId() ? newSolution : oldSolution);
                        log.info(String.valueOf(latestSolutions.size()));
                        return FileVisitResult.CONTINUE;
                    }
                });

                latestSolutions
                        .values()
                        .forEach(
                                (request) ->
                                        distributorService.put(request.taskId(),
                                                request.solutionId(),
                                                request.userId(),
                                                request.lang().getName(),
                                                request.program()));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
