package com.taskboard.service;

import com.taskboard.dto.BoardRequest;
import com.taskboard.dto.BoardResponse;
import com.taskboard.event.BoardEvent;
import com.taskboard.exception.BoardNotFoundException;
import com.taskboard.model.Board;
import com.taskboard.repository.BoardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BoardService {

    private final BoardRepository boardRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<BoardResponse> getAllBoards() {
        return boardRepository.findByDeletedFalseOrderByCreatedAtDesc()
                .stream()
                .map(BoardResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BoardResponse getBoardById(UUID boardId) {
        Board board = boardRepository.findByIdAndDeletedFalse(boardId)
                .orElseThrow(() -> new BoardNotFoundException(boardId));
        return BoardResponse.fromEntity(board);
    }

    @Transactional
    public BoardResponse createBoard(BoardRequest request) {
        Board board = Board.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();

        Board savedBoard = boardRepository.save(board);
        BoardResponse response = BoardResponse.fromEntity(savedBoard);

        eventPublisher.publishEvent(BoardEvent.created(response));
        log.info("Created board: {}", savedBoard.getId());

        return response;
    }

    @Transactional
    public BoardResponse updateBoard(UUID boardId, BoardRequest request) {
        Board board = boardRepository.findByIdAndDeletedFalse(boardId)
                .orElseThrow(() -> new BoardNotFoundException(boardId));

        board.setName(request.getName());
        board.setDescription(request.getDescription());

        Board updatedBoard = boardRepository.save(board);
        BoardResponse response = BoardResponse.fromEntity(updatedBoard);

        eventPublisher.publishEvent(BoardEvent.updated(response));
        log.info("Updated board: {}", boardId);

        return response;
    }

    @Transactional
    public void deleteBoard(UUID boardId) {
        Board board = boardRepository.findByIdAndDeletedFalse(boardId)
                .orElseThrow(() -> new BoardNotFoundException(boardId));

        board.setDeleted(true);
        boardRepository.save(board);

        eventPublisher.publishEvent(BoardEvent.deleted(boardId));
        log.info("Soft-deleted board: {}", boardId);
    }
}
