alter table comments
    add column edited_at timestamp with time zone,
    add column deleted_at timestamp with time zone;

-- Preserve the previous UI rule for comments edited before lifecycle tracking.
update comments
set edited_at = updated_at
where updated_at > created_at;

create index idx_comments_parent_comment_id on comments (parent_comment_id);
