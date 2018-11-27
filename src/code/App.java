package code;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

public class App {
	private static final int COLUMN_STATUS = 0;
	private static final int COLUMN_IP = 1;
	private static final int COLUMN_PING = 2;
	private JFrame frame;
	private JTable table;
	private String startIP;
	private String stopIP;
	private String currentIP;
	private ThreadPoolExecutor executorService;
	private JTextField maxThread;
	private JTextField maxTimeout;
	private JTextField start;
	private JTextField end;

	private JLabel online;
	private JLabel offline;
	private JLabel pinging;
	private JLabel thread;

	private int onlineCount, offlineCount, pingCount;

	public App() {
		this.frame = new JFrame("IP Scanner");
		this.start = new JTextField(10);
		this.end = new JTextField(10);
		this.maxThread = new JTextField(5);
		this.maxThread.setText("10");
		this.maxTimeout = new JTextField(5);
		this.maxTimeout.setText("5000");
		this.online = new JLabel("Online: 0");
		this.offline = new JLabel("Offline: 0");
		this.pinging = new JLabel("Pinging: 0");
		this.thread = new JLabel("Threads: 0");

		JPanel startPanel = new JPanel();
		startPanel.setLayout(new BoxLayout(startPanel, BoxLayout.X_AXIS));
		startPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
		JLabel from = new JLabel("From:");
		startPanel.add(from);
		startPanel.add(start);

		JPanel endPanel = new JPanel();
		endPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
		endPanel.setLayout(new BoxLayout(endPanel, BoxLayout.X_AXIS));
		JLabel to = new JLabel("To:");
		endPanel.add(to);
		endPanel.add(end);

		JPanel maxThreadPanel = new JPanel();
		maxThreadPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
		maxThreadPanel.setLayout(new BoxLayout(maxThreadPanel, BoxLayout.X_AXIS));
		JLabel maxLabel = new JLabel("Thread:");
		maxThreadPanel.add(maxLabel);
		maxThreadPanel.add(maxThread);

		JPanel maxTimeoutPanel = new JPanel();
		maxTimeoutPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
		maxTimeoutPanel.setLayout(new BoxLayout(maxTimeoutPanel, BoxLayout.X_AXIS));
		JLabel timeoutLabel = new JLabel("Timeout(ms):");
		maxTimeoutPanel.add(timeoutLabel);
		maxTimeoutPanel.add(maxTimeout);

		JButton button = new JButton("Scan now");
		button.addActionListener(this::onScanClicked);

		FlowLayout layout = new FlowLayout(FlowLayout.CENTER);
		layout.setHgap(20);
		JPanel optionPane = new JPanel(layout);
		optionPane.setPreferredSize(new Dimension(200, 80));
		optionPane.add(startPanel);
		optionPane.add(endPanel);
		optionPane.add(maxThreadPanel);
		optionPane.add(maxTimeoutPanel);
		optionPane.add(button);

		String[] columnNames = { "Status", "IP Address", "Ping" };
		Object[][] initialData = {};

		TableModel tableModel = new DefaultTableModel(initialData, columnNames) {
			private static final long serialVersionUID = 1L;

			public Class getColumnClass(int column) {
				return getValueAt(0, column).getClass();
			}
		};
		this.table = new JTable(tableModel);
		table.getColumnModel().getColumn(0).setMaxWidth(50);

		TableRowSorter<TableModel> sorter = new TableRowSorter<TableModel>(this.table.getModel());
		table.setRowSorter(sorter);
		JScrollPane scrollPane = new JScrollPane(table);

		JPanel bottomBar = new JPanel();
		bottomBar.setLayout(new FlowLayout(FlowLayout.LEFT, 20, 0));
		bottomBar.setBorder(new EmptyBorder(4, 0, 4, 4));
		bottomBar.add(online);
		bottomBar.add(offline);
		bottomBar.add(pinging);
		bottomBar.add(thread);

		frame.add(optionPane, BorderLayout.NORTH);
		frame.add(scrollPane, BorderLayout.CENTER);
		frame.add(bottomBar, BorderLayout.SOUTH);

		try {
			InetAddress localhost = Inet4Address.getLocalHost();
			NetworkInterface networkInterface = NetworkInterface.getByInetAddress(localhost);
			InterfaceAddress interfaceAddress = this.getInterfaceAddress(networkInterface);

			int prefixLength = interfaceAddress.getNetworkPrefixLength();
			String[] buffer = new String[localhost.getAddress().length];
			String subnet = new String(new char[32]).replace('\0', '0');
			String prefix = subnet.substring(0, prefixLength).replaceAll("0", "1");
			String suffix = subnet.substring(prefixLength, subnet.length());
			subnet = prefix + suffix;

			for (int i = 0; i < localhost.getAddress().length; i++) {
				int mask = Integer.parseInt(subnet.substring(i * 8, i * 8 + 8), 2);
				buffer[i] = String.valueOf(localhost.getAddress()[i] & mask);
			}

			this.startIP = String.join(".", buffer);
			this.stopIP = interfaceAddress.getBroadcast().getHostAddress();
			this.currentIP = this.startIP;
			start.setText(startIP);
			end.setText(stopIP);
		} catch (UnknownHostException | SocketException e) {
			e.printStackTrace();
		}
	}

	private InterfaceAddress getInterfaceAddress(NetworkInterface networkInterface) {
		for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
			if (interfaceAddress.getBroadcast() != null) {
				return interfaceAddress;
			}
		}
		return null;
	}

	private void onScanClicked(ActionEvent e) {
		if (executorService != null) {
			executorService.shutdownNow();
		}

		this.startIP = this.start.getText();
		this.stopIP = this.end.getText();
		this.currentIP = this.startIP;
		this.offlineCount = 0;
		this.onlineCount = 0;
		this.pingCount = 0;

		executorService = (ThreadPoolExecutor) Executors.newFixedThreadPool(Integer.parseInt(maxThread.getText()));

		DefaultTableModel tableModel = (DefaultTableModel) this.table.getModel();
		tableModel.setRowCount(0);
		while (hasNextIP()) {
			nextIP();
			String currentIP = this.currentIP;
			Future<InetAddress> future = executorService.submit(() -> {
				this.thread.setText(
						"Thread: " + executorService.getActiveCount() + "/" + executorService.getMaximumPoolSize());
				if (Thread.currentThread().isInterrupted()) {
					return null;
				}
				tableModel.addRow(new Object[] { new ImageIcon(this.getClass().getResource("/assets/yellow.png")),
						currentIP, "" });
				long startTime = System.currentTimeMillis();
				updateNumber(0);
				this.pinging.setText("Pinging: " + this.pingCount);
				boolean isReachable = Inet4Address.getByName(currentIP)
						.isReachable(Integer.parseInt(this.maxTimeout.getText()));
				if (Thread.currentThread().isInterrupted()) {
					return null;
				}
				if (isReachable) {
					long ping = System.currentTimeMillis() - startTime;
					tableModel.setValueAt(new ImageIcon(this.getClass().getResource("/assets/green.png")),
							getRowIndex(currentIP), COLUMN_STATUS);
					tableModel.setValueAt(ping + "ms", getRowIndex(currentIP), COLUMN_PING);
					updateNumber(1);
					this.online.setText("Online: " + this.onlineCount);
					return Inet4Address.getByName(currentIP);
				} else {
					tableModel.setValueAt(new ImageIcon(this.getClass().getResource("/assets/red.png")),
							getRowIndex(currentIP), COLUMN_STATUS);
					tableModel.setValueAt("-", getRowIndex(currentIP), COLUMN_PING);
					updateNumber(-1);
					this.offline.setText("Offline: " + this.offlineCount);
					return null;
				}
			});
		}
	}

	private synchronized void updateNumber(int amount) {
		if (amount == 1) {
			this.onlineCount++;
			this.pingCount--;
		} else if (amount == 0) {
			this.pingCount++;
		} else {
			this.offlineCount++;
			this.pingCount--;
		}
	}

	private int getRowIndex(String ip) {
		DefaultTableModel tableModel = (DefaultTableModel) this.table.getModel();
		for (int i = 0; i < tableModel.getRowCount(); i++) {
			if (tableModel.getValueAt(i, COLUMN_IP).equals(ip))
				return i;
		}
		return -1;
	}

	private void nextIP() {
		String currentIPs = Arrays.stream(this.currentIP.split("\\.")).mapToInt(Integer::parseInt).mapToObj(x -> {
			String unpadded = Integer.toBinaryString(x);
			String padded = "00000000".substring(unpadded.length()) + unpadded;
			return padded;
		}).collect(Collectors.joining());
		String nextBinaryIP = Long.toBinaryString(Long.parseLong(currentIPs, 2) + 1);
		String nextIP = Arrays.stream(nextBinaryIP.split("(?<=\\G........)"))
				.map(x -> String.valueOf(Integer.parseInt(x, 2))).collect(Collectors.joining("."));
		this.currentIP = nextIP;
	}

	private boolean hasNextIP() {
		return !this.currentIP.equals(this.stopIP);
	}

	private void start() {
		frame.pack();
		frame.setAlwaysOnTop(true);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setVisible(true);
	}

	public static void main(String[] arÏgs) {
		App app = new App();
		app.start();
	}
}
