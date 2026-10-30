package de.nick1st.imm_ptl.events;


import qouteall.imm_ptl.core.portal.Portal;

public abstract class PortalEvent {
    public final Portal portal;

    protected PortalEvent(Portal portal) {
        this.portal = portal;
    }
}
