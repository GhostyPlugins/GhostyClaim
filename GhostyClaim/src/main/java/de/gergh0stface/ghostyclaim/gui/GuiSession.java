package de.gergh0stface.ghostyclaim.gui;

import java.util.UUID;

/**
 * Represents the state of a GUI a player currently has open.
 */
public class GuiSession {

    public enum Type {
        MAIN, FLAGS, TRUST, LIST, DELETE_CONFIRM,
        ADMIN_LIST, ADMIN_DETAIL, ADMIN_DELETE_CONFIRM
    }

    private final Type   type;
    private final UUID   claimId;
    private final int    page;
    private final String filter; // optional owner-name filter for admin list

    public GuiSession(Type type, UUID claimId) {
        this(type, claimId, 0, null);
    }

    public GuiSession(Type type, UUID claimId, int page) {
        this(type, claimId, page, null);
    }

    public GuiSession(Type type, UUID claimId, int page, String filter) {
        this.type    = type;
        this.claimId = claimId;
        this.page    = page;
        this.filter  = filter;
    }

    public Type   getType()    { return type;    }
    public UUID   getClaimId() { return claimId; }
    public int    getPage()    { return page;    }
    public String getFilter()  { return filter;  }
}
