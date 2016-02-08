import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.*;
import java.util.*;

public class WebProxy {
	/** Port for the proxy */
	private static int port;

	/** Socket for client connections */
	private static ServerSocket socket;
	
	public static void main(String args[]) {
		/** Read command line arguments and start proxy */
		/** Read port number as command-line argument **/
		port = Integer.parseInt(args[0]);
		
		/** Create a server socket, bind it to a port and start listening **/
		try {
			socket = new ServerSocket(port);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		Socket client = null;
		Map<String, File> cacheMap = new HashMap<String, File>();
		int fileNumber = 0;
		int serverPort = 80;
		
		/** Main loop. Listen for incoming connections **/
		while (true) {
			String 	firstLine = null, 
					subsequentLines = "", 
					host = null, 
					temp = null, 
					method = null, 
					URI = null, 
					version = null;
			try {
				client = socket.accept();
				System.out.println("'Received a connection from: " + client);

				/** Read client's HTTP request **/
				Scanner in = new Scanner(client.getInputStream());

				firstLine = in.nextLine();	
				String[] tmp = firstLine.split(" ");
				method = tmp[0];
				URI = tmp[1];
				version = tmp[2];
				while(!(temp = in.nextLine()).isEmpty())
				{
					tmp = temp.split(" ");
					if(tmp[0].equals("Host:"))
					{	
						subsequentLines += temp + "\n";
						break;
					}
				}
				System.out.println(firstLine);
				System.out.print(subsequentLines);

				URI uri = new URI(URI);
				if((serverPort = uri.getPort()) == -1)
					serverPort = 80;
				host = uri.getHost();
				
			} 
			catch (IOException e) 
			{
				System.out.println("Error reading request from client: " + e);
				/* Definitely cannot continue, so skip to next
				 * iteration of while loop. */
				continue;
			}
			catch (NoSuchElementException e)
			{
				e.printStackTrace();
				continue;
			}
			catch (URISyntaxException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			/** Check cache if file exists **/
			
		//	File f = new File(URI);
			if (cacheMap.containsKey(URI))//.exists())
			{
				System.out.println("Request found in cache");
				if(objectNotModified(cacheMap.get(URI), host, firstLine, subsequentLines, 80))
				{
					/** Read the file **/
					byte[] fileArray = null;
					try {
					//	fileArray = Files.readAllBytes(Paths.get(URI,URI));
						fileArray = Files.readAllBytes(cacheMap.get(URI).toPath());

					/** generate appropriate respond headers and send the file contents **/
						BufferedOutputStream bos = new BufferedOutputStream(client.getOutputStream());
						bos.write(fileArray);
						bos.flush();
						bos.close();
						client.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					System.out.println("Cached object sent to client");
				}	
				else
				{
					/** connect to server and relay client's request **/
					try {
						System.out.println("Object modified. Requesting new one");
						Socket server = new Socket(host, serverPort);
						sendRequestToServer(server, serverPort, firstLine, subsequentLines);
						System.out.println("Request sent to server");
						
						/** Get response from server **/
						byte[] data = getResponseFromServer(server, cacheMap.get(URI));
						server.close();
						
						/** Censor the data **/
						if(data!=null)
						{
							data = censor(data);
							FileOutputStream fos = new FileOutputStream(cacheMap.get(URI));
							fos.write(data);
							fos.close();
						}
						/** Cache the contents as appropriate **/
			//			cacheMap.put(URI, file);
						
						/** Send respose to client **/
						sendResponseToClient(client, cacheMap.get(URI));
						System.out.println("Sent response to client");
						client.close();
					} catch (UnknownHostException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
			else {
				try {
					/** connect to server and relay client's request **/
					System.out.println("Request not found in cache, sending request to server");
					Socket server = new Socket(host, serverPort);
					sendRequestToServer(server, serverPort, firstLine, subsequentLines);
					System.out.println("Request sent to server");
					
					/** Get response from server **/
					File file = new File("cached_files\\" + Integer.toString(fileNumber++));
					byte[] data = getResponseFromServer(server, file);
					server.close();
					
					/** Censor the data **/
					if(data != null)
					{
						data = censor(data);					
						/** Cache the contents as appropriate **/
	//					File file = new File("cached_files\\" + Integer.toString(fileNumber++));
						FileOutputStream fos = new FileOutputStream(file);
						fos.write(data);
						fos.close();
						System.out.println("Object added to cache");
					}		
					cacheMap.put(URI, file);
			
					/** Send respose to client **/
					sendResponseToClient(client, file);
					System.out.println("Sent response to client");
					client.close();
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public static void sendRequestToServer(Socket server, int serverPort, String firstLine, String subsequentLines)
	{
		PrintWriter toServer = null;
		try {
			toServer = new PrintWriter(server.getOutputStream());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		toServer.println(firstLine);
		if(subsequentLines != null)
			toServer.print(subsequentLines);
		toServer.println("");
		toServer.flush();		
	//	toServer.close();
	}
	public static byte[] getResponseFromServer(Socket server, File file)
	{
		InputStream inputStream = null;
		byte[] data = null;
		try 
		{
			inputStream = server.getInputStream();
			Files.copy(inputStream, file.toPath());
			Scanner fileScanner = new Scanner(file);
			for(int i=0; i<30 && fileScanner.hasNextLine(); i++)
				if(fileScanner.nextLine().contains("Content-Type: text/html"))
				{
					fileScanner.close();
					data = Files.readAllBytes(file.toPath());
					break;
				}
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		return data;
	}
	
	public static void sendResponseToClient(Socket client, File file)
	{
		OutputStream outputStream;
		try
		{
			outputStream = client.getOutputStream();
			Files.copy(file.toPath(), outputStream);
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static boolean objectNotModified(File file, String host, String firstLine, String subsequentLines, int serverPort)
	{
		Scanner fileScanner = null;
		try {
			fileScanner = new Scanner(file);
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}
		String HTTPdate = null;
		while(fileScanner.hasNext())
		{
			if(fileScanner.next().equals("Last-Modified:"))
			{
				HTTPdate = fileScanner.nextLine().trim();
				break;
			}
			fileScanner.nextLine();
		}
		fileScanner.close();
		if(HTTPdate == null)
			return true;
		
		Socket server = null;
		String HTTPstatus = null;
		boolean notModified = false;
		try {
			/** Send Request to server **/
			server = new Socket(host, serverPort);//80 or serverPort?????
			System.out.println(subsequentLines + "If-Modified-Since: " + HTTPdate);
			sendRequestToServer(server, serverPort, firstLine, subsequentLines + "If-Modified-Since: " + HTTPdate + "\n" + "");
			System.out.println("Conditional Request sent");
			/** Get response from server **/
			Scanner in = new Scanner(server.getInputStream());

			if(in.hasNext())
				in.next();
			HTTPstatus = in.next();

			in.close();	
			server.close();
			
		} catch (UnknownHostException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		return HTTPstatus.equals("304");
	}
	public static byte[] censor(byte[] data)
	{
		String dataString = new String(data);
	//	System.out.println(dataString);
		if(!dataString.contains("Content-Type: text/html"))
			return data;
		System.out.println("Censoring");

		File file = new File("censor.txt");
		Scanner fileScanner = null;
		try {
			fileScanner = new Scanner(file);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		String target = null;
		while(fileScanner.hasNextLine())
		{
			target = fileScanner.nextLine();
			dataString = dataString.replace(target, "---");
		}
		return dataString.getBytes();
	}
}
