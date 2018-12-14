public class FileProperties
{
    private int lock;
    private boolean isLocal;
    private Integer localNode;

    public FileProperties(int lock, boolean isLocal, Integer localNode)
    {
        this.lock = lock;
        this.isLocal = isLocal;
        this.localNode = localNode;
    }

    public Integer getLocalNode()
    {
        return localNode;
    }

    public int getLock()
    {
        return lock;
    }

    public boolean getIsLocal()
    {
        return isLocal;
    }
}
