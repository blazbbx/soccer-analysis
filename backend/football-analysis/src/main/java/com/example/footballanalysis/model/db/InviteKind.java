package com.example.footballanalysis.model.db;

import com.example.footballanalysis.model.db.user.UserRole;

public enum InviteKind {
    PARENT,
    PLAYER_SELF,
    COACH,
    FAN,
    CHILD_LOGIN;

    public UserRole invitedRole() {
        return switch (this) {
            case PARENT -> UserRole.PARENT;
            case PLAYER_SELF, CHILD_LOGIN -> UserRole.PLAYER;
            case COACH -> UserRole.COACH;
            case FAN -> UserRole.FAN;
        };
    }

    public static InviteKind fromRole(UserRole role) {
        if (role == null) {
            return null;
        }
        return switch (role) {
            case PARENT -> PARENT;
            case PLAYER -> PLAYER_SELF;
            case COACH -> COACH;
            case FAN -> FAN;
            case ADMIN -> null;
        };
    }
}
