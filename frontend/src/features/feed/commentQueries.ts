import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createComment, deleteComment, listComments } from '../../api/posts';
import type { Comment } from '../../api/types';
import { updateCachedPost } from './postQueries';

const commentKey = (postId: string) => ['comments', postId] as const;

export function useComments(postId: string) {
  return useQuery({
    queryKey: commentKey(postId),
    queryFn: ({ signal }) => listComments(postId, signal),
  });
}

export function useAddComment(postId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (content: string) => createComment(postId, content),
    onSuccess: (comment) => {
      queryClient.setQueryData<Comment[]>(commentKey(postId), (comments = []) => [...comments, comment]);
      updateCachedPost(queryClient, postId, (post) => ({ ...post, commentCount: post.commentCount + 1 }));
    },
  });
}

export function useDeleteComment(postId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (commentId: string) => deleteComment(postId, commentId),
    onSuccess: (_result, commentId) => {
      queryClient.setQueryData<Comment[]>(commentKey(postId), (comments = []) =>
        comments.filter((comment) => comment.id !== commentId),
      );
      updateCachedPost(queryClient, postId, (post) => ({
        ...post,
        commentCount: Math.max(0, post.commentCount - 1),
      }));
    },
  });
}
