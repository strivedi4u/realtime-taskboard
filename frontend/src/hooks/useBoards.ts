import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { boardApi } from '@/services/api'
import type { CreateBoardRequest } from '@/types'

export const BOARDS_QUERY_KEY = ['boards']

export function useBoards() {
  return useQuery({
    queryKey: BOARDS_QUERY_KEY,
    queryFn: boardApi.getAll,
  })
}

export function useBoard(boardId: string) {
  return useQuery({
    queryKey: ['boards', boardId],
    queryFn: () => boardApi.getById(boardId),
    enabled: !!boardId,
  })
}

export function useCreateBoard() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (data: CreateBoardRequest) => boardApi.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: BOARDS_QUERY_KEY })
    },
  })
}

export function useUpdateBoard() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ boardId, data }: { boardId: string; data: CreateBoardRequest }) =>
      boardApi.update(boardId, data),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: BOARDS_QUERY_KEY })
      queryClient.invalidateQueries({ queryKey: ['boards', variables.boardId] })
    },
  })
}

export function useDeleteBoard() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (boardId: string) => boardApi.delete(boardId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: BOARDS_QUERY_KEY })
    },
  })
}
