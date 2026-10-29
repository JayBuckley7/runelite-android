package java.awt;

public class Dialog extends Window {
    private static final long serialVersionUID = 5036553639616394035L;

    public enum ModalityType {
        MODELESS, DOCUMENT_MODAL, APPLICATION_MODAL, TOOLKIT_MODAL
    }

    public enum ModalExclusionType {
        NO_EXCLUDE, APPLICATION_EXCLUDE, TOOLKIT_EXCLUDE
    }

    private String title = "";
    private ModalityType modality = ModalityType.MODELESS;
    private boolean resizable = true, undecorated;
    private final Object modalLock = new Object();
    private volatile SecondaryLoop modalLoop;

    public Dialog(Frame owner) { this((Window) owner); }
    public Dialog(Frame owner, String title) { this((Window) owner, title); }
    public Dialog(Frame owner, boolean modal) { this(owner, "", modal); }
    public Dialog(Frame owner, String title, boolean modal) { this((Window) owner, title, modal ? ModalityType.APPLICATION_MODAL : ModalityType.MODELESS); }
    public Dialog(Frame owner, String title, boolean modal, GraphicsConfiguration configuration) { this((Window) owner, title, modal ? ModalityType.APPLICATION_MODAL : ModalityType.MODELESS, configuration); }
    public Dialog(Dialog owner) { this((Window) owner); }
    public Dialog(Dialog owner, String title) { this((Window) owner, title); }
    public Dialog(Dialog owner, String title, boolean modal) { this((Window) owner, title, modal ? ModalityType.APPLICATION_MODAL : ModalityType.MODELESS); }
    public Dialog(Dialog owner, String title, boolean modal, GraphicsConfiguration configuration) { this((Window) owner, title, modal ? ModalityType.APPLICATION_MODAL : ModalityType.MODELESS, configuration); }
    public Dialog(Window owner) { this(owner, ""); }
    public Dialog(Window owner, String title) { this(owner, title, ModalityType.MODELESS); }
    public Dialog(Window owner, ModalityType modality) { this(owner, "", modality); }
    public Dialog(Window owner, String title, ModalityType modality) { this(owner, title, modality, null); }
    public Dialog(Window owner, String title, ModalityType modality, GraphicsConfiguration configuration) {
        super(owner, configuration); setTitle(title); setModalityType(modality);
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title == null ? "" : title; }
    public ModalityType getModalityType() { return modality; }
    public void setModalityType(ModalityType type) { this.modality = type == null ? ModalityType.MODELESS : type; }
    public boolean isModal() { return modality != ModalityType.MODELESS; }
    public void setModal(boolean modal) { this.modality = modal ? ModalityType.APPLICATION_MODAL : ModalityType.MODELESS; }
    public boolean isResizable() { return resizable; }
    public void setResizable(boolean resizable) { this.resizable = resizable; }
    public boolean isUndecorated() { return undecorated; }
    public void setUndecorated(boolean undecorated) { this.undecorated = undecorated; }
    @Override public void setVisible(boolean visible) {
        boolean block = visible && isModal() && !isVisible();
        SecondaryLoop loop = block && EventQueue.isDispatchThread() ? new EventQueue().createSecondaryLoop() : null;
        if (loop != null) modalLoop = loop;
        super.setVisible(visible);
        if (!visible) {
            synchronized (modalLock) { modalLock.notifyAll(); }
            SecondaryLoop current = modalLoop;
            if (current != null) {
                current.exit();
                // Also wake a loop whose enter races this hide operation.
                EventQueue.invokeLater(current::exit);
            }
        } else if (block) {
            if (loop != null) {
                try { if (isVisible()) loop.enter(); } finally { if (modalLoop == loop) modalLoop = null; }
            } else {
                synchronized (modalLock) {
                    while (isVisible()) try { modalLock.wait(); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                }
            }
        }
    }
}
