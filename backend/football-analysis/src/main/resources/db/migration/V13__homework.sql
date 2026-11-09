-- Házi feladat: a lecke (homeworks), a tartalomblokkok (homework_items), a címzettenkénti kiosztás (homework_assignments),
-- a kérdésenkénti válasz (homework_answers), a kiosztás üzenetszála (homework_comments) és a vázlat címzettjei.
-- Új táblák, ezért backfill nincs. A meglévő sorokhoz nem nyúl.
CREATE TABLE homeworks (
    id uuid NOT NULL,
    team_id uuid NOT NULL,
    created_by_user_id uuid,
    title character varying(200) NOT NULL,
    description text,
    status character varying(20) NOT NULL,
    published_at timestamp(6) without time zone,
    due_at timestamp(6) without time zone,
    allow_late boolean DEFAULT true NOT NULL,
    draft_entire_roster boolean DEFAULT false NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT pk_homeworks PRIMARY KEY (id),
    CONSTRAINT fk_homeworks_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
    CONSTRAINT fk_homeworks_created_by FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT ck_homeworks_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_homeworks_published CHECK (status = 'DRAFT' OR published_at IS NOT NULL)
);
CREATE INDEX ix_homeworks_team_status_due ON homeworks (team_id, status, due_at, id);

CREATE TABLE homework_items (
    id uuid NOT NULL,
    homework_id uuid NOT NULL,
    position integer NOT NULL,
    type character varying(20) NOT NULL,
    body text,
    clip_id uuid,
    focus_ms integer,
    question_kind character varying(20),
    options jsonb,
    correct_options jsonb,
    required boolean DEFAULT true NOT NULL,
    ref_item_id uuid,
    CONSTRAINT pk_homework_items PRIMARY KEY (id),
    CONSTRAINT fk_homework_items_homework FOREIGN KEY (homework_id) REFERENCES homeworks(id) ON DELETE CASCADE,
    CONSTRAINT fk_homework_items_clip FOREIGN KEY (clip_id) REFERENCES clips(id) ON DELETE SET NULL,
    CONSTRAINT fk_homework_items_ref FOREIGN KEY (ref_item_id) REFERENCES homework_items(id) ON DELETE SET NULL,
    CONSTRAINT ck_homework_items_type CHECK (type IN ('TEXT', 'CLIP', 'QUESTION')),
    CONSTRAINT ck_homework_items_question CHECK ((type = 'QUESTION') = (question_kind IS NOT NULL))
);
CREATE INDEX ix_homework_items_homework ON homework_items (homework_id, position);
CREATE INDEX ix_homework_items_clip ON homework_items (clip_id) WHERE clip_id IS NOT NULL;

CREATE TABLE homework_assignments (
    id uuid NOT NULL,
    homework_id uuid NOT NULL,
    player_profile_id uuid NOT NULL,
    status character varying(20) NOT NULL,
    response_text text,
    assigned_at timestamp(6) without time zone NOT NULL,
    first_viewed_at timestamp(6) without time zone,
    submitted_at timestamp(6) without time zone,
    submitted_by_user_id uuid,
    reviewed_at timestamp(6) without time zone,
    reviewed_by_user_id uuid,
    coach_feedback text,
    -- Az edző utolsó visszaküldése javításra: mikor, ki és miért. A javítás beadása után is megmarad a határidő-jelzőkhöz.
    returned_at timestamp(6) without time zone,
    returned_by_user_id uuid,
    return_note text,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT pk_homework_assignments PRIMARY KEY (id),
    CONSTRAINT fk_homework_assignments_homework FOREIGN KEY (homework_id) REFERENCES homeworks(id) ON DELETE CASCADE,
    CONSTRAINT fk_homework_assignments_profile FOREIGN KEY (player_profile_id) REFERENCES player_profiles(id) ON DELETE CASCADE,
    CONSTRAINT fk_homework_assignments_submitted_by FOREIGN KEY (submitted_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_homework_assignments_reviewed_by FOREIGN KEY (reviewed_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_homework_assignments_returned_by FOREIGN KEY (returned_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT ux_homework_assignments_homework_profile UNIQUE (homework_id, player_profile_id),
    -- RETURNED: az edző javításra visszaküldte. Újra a teendők között van, de a határidő már nem zárja le.
    CONSTRAINT ck_homework_assignments_status CHECK (status IN ('ASSIGNED', 'SUBMITTED', 'RETURNED', 'COMPLETED'))
);
CREATE INDEX ix_homework_assignments_profile_status ON homework_assignments (player_profile_id, status);
CREATE INDEX ix_homework_assignments_homework_status ON homework_assignments (homework_id, status);

CREATE TABLE homework_answers (
    assignment_id uuid NOT NULL,
    item_id uuid NOT NULL,
    text_answer text,
    selected_options jsonb,
    timestamp_ms integer,
    CONSTRAINT pk_homework_answers PRIMARY KEY (assignment_id, item_id),
    CONSTRAINT fk_homework_answers_assignment FOREIGN KEY (assignment_id) REFERENCES homework_assignments(id) ON DELETE CASCADE,
    CONSTRAINT fk_homework_answers_item FOREIGN KEY (item_id) REFERENCES homework_items(id) ON DELETE CASCADE
);
CREATE INDEX ix_homework_answers_item ON homework_answers (item_id);

CREATE TABLE homework_comments (
    id uuid NOT NULL,
    assignment_id uuid NOT NULL,
    author_user_id uuid,
    body character varying(2000) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT pk_homework_comments PRIMARY KEY (id),
    CONSTRAINT fk_homework_comments_assignment FOREIGN KEY (assignment_id) REFERENCES homework_assignments(id) ON DELETE CASCADE,
    CONSTRAINT fk_homework_comments_author FOREIGN KEY (author_user_id) REFERENCES users(id) ON DELETE SET NULL
);
CREATE INDEX ix_homework_comments_assignment ON homework_comments (assignment_id, created_at, id);

CREATE TABLE homework_draft_recipients (
    homework_id uuid NOT NULL,
    player_profile_id uuid NOT NULL,
    CONSTRAINT pk_homework_draft_recipients PRIMARY KEY (homework_id, player_profile_id),
    CONSTRAINT fk_homework_draft_recipients_homework FOREIGN KEY (homework_id) REFERENCES homeworks(id) ON DELETE CASCADE,
    CONSTRAINT fk_homework_draft_recipients_profile FOREIGN KEY (player_profile_id) REFERENCES player_profiles(id) ON DELETE CASCADE
);
CREATE INDEX ix_homework_draft_recipients_profile ON homework_draft_recipients (player_profile_id);

-- A többi FK-oszlop indexe, a V2 elve szerint. A fenti összetett indexek a többit fedik.
CREATE INDEX ix_homeworks_created_by ON homeworks (created_by_user_id);
CREATE INDEX ix_homework_items_ref ON homework_items (ref_item_id) WHERE ref_item_id IS NOT NULL;
CREATE INDEX ix_homework_assignments_submitted_by ON homework_assignments (submitted_by_user_id);
CREATE INDEX ix_homework_assignments_reviewed_by ON homework_assignments (reviewed_by_user_id);
CREATE INDEX ix_homework_assignments_returned_by ON homework_assignments (returned_by_user_id);
CREATE INDEX ix_homework_comments_author ON homework_comments (author_user_id);
