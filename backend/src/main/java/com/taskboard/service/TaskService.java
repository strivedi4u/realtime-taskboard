package com.taskboard.service;

import com.taskboard.dto.TaskMoveRequest;
import com.taskboard.dto.TaskRequest;
import com.taskboard.dto.TaskResponse;
import com.taskboard.event.TaskEvent;
import com.taskboard.exception.BoardNotFoundException;
import com.taskboard.exception.InvalidStatusTransitionException;
import com.taskboard.exception.TaskNotFoundException;
import com.taskboard.model.Board;
import com.taskboard.model.Task;
import com.taskboard.model.TaskStatus;
import com.taskboard.repository.BoardRepository;
import com.taskboard.repository.TaskRepository;
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
public class TaskService {

    private final TaskRepository taskRepository;
    private final BoardRepository boardRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<TaskResponse> getTasksByBoardId(UUID boardId) {
        if (!boardRepository.existsById(boardId)) {
            throw new BoardNotFoundException(boardId);
        }
        return taskRepository.findByBoardIdOrderByCreatedAtDesc(boardId)
                .stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TaskResponse getTaskById(UUID taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));
        return TaskResponse.fromEntity(task);
    }

    @Transactional
    public TaskResponse createTask(UUID boardId, TaskRequest request) {
        Board board = boardRepository.findByIdAndDeletedFalse(boardId)
                .orElseThrow(() -> new BoardNotFoundException(boardId));

        Task task = Task.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .priority(request.getPriority())
                .assigneeId(request.getAssigneeId())
                .dueDate(request.getDueDate())
                .board(board)
                .build();

        Task savedTask = taskRepository.save(task);
        TaskResponse response = TaskResponse.fromEntity(savedTask);

        eventPublisher.publishEvent(TaskEvent.created(response));
        log.info("Created task: {} on board: {}", savedTask.getId(), boardId);

        return response;
    }

    @Transactional
    public TaskResponse updateTask(UUID taskId, TaskRequest request) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));

        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        if (request.getPriority() != null) {
            task.setPriority(request.getPriority());
        }
        task.setAssigneeId(request.getAssigneeId());
        task.setDueDate(request.getDueDate());

        Task updatedTask = taskRepository.save(task);
        TaskResponse response = TaskResponse.fromEntity(updatedTask);

        eventPublisher.publishEvent(TaskEvent.updated(response));
        log.info("Updated task: {}", taskId);

        return response;
    }

    @Transactional
    public TaskResponse moveTask(UUID taskId, TaskMoveRequest request) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));

        TaskStatus currentStatus = task.getStatus();
        TaskStatus newStatus = request.getStatus();

        validateStatusTransition(currentStatus, newStatus);

        task.setStatus(newStatus);
        Task updatedTask = taskRepository.save(task);
        TaskResponse response = TaskResponse.fromEntity(updatedTask);

        eventPublisher.publishEvent(TaskEvent.moved(response, currentStatus, newStatus));
        log.info("Moved task {} from {} to {}", taskId, currentStatus, newStatus);

        return response;
    }

    @Transactional
    public void deleteTask(UUID taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));

        UUID boardId = task.getBoard().getId();
        taskRepository.delete(task);

        eventPublisher.publishEvent(TaskEvent.deleted(taskId, boardId));
        log.info("Deleted task: {}", taskId);
    }

    // This method should be called in moveTask but is currently not
    private void validateStatusTransition(TaskStatus from, TaskStatus to) {
        if (from == to) {
            return; // No change
        }

        boolean isValid = switch (from) {
            case TODO -> to == TaskStatus.IN_PROGRESS;
            case IN_PROGRESS -> to == TaskStatus.TODO || to == TaskStatus.DONE;
            case DONE -> to == TaskStatus.IN_PROGRESS;
        };

        if (!isValid) {
            throw new InvalidStatusTransitionException(from, to);
        }
    }
}
