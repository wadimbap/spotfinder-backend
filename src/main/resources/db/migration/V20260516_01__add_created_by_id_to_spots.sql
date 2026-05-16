alter table spots
    add column created_by_user_id uuid;

alter table spots
    add constraint fk_spots_created_by_user
        foreign key (created_by_user_id)
            references users (id);