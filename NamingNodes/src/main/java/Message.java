public class Message
{
    String type = null;
    String message = null;

    public Message(String type, String message)
    {
        this.type = type;
        this.message = message;
    }

    public String getType()
    {
        return type;
    }

    public String getMessage()
    {
        return message;
    }
}
