package main;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import message.*;

public class Server {
	Map<Integer, BankUser> userList;
	Map<Integer, BankAccount> accountList;
	Map<Integer, Teller> tellerList;

	Map<Integer, Boolean> activeUsers;
	Map<Integer, Boolean> activeAccounts;
	Map<Integer, Boolean> activeTellers;

	List<String> logs = null;
	BufferedWriter logger = null;
	String logsFilename = "logs.txt";
	Scanner scanner = null;

	public Server() {
		userList = new HashMap<>();
		accountList = new HashMap<>();
		tellerList = new HashMap<>();
		activeUsers = new HashMap<>();
		activeAccounts = new HashMap<>();
		activeTellers = new HashMap<>();
		logs = new ArrayList<>();
	}

	public static void main(String[] args) {
		new Server().go();
	}

	public void hardCodeSetUp() {
		String name = "Donny";
		String birthday = "04/23/2000";
		String password = "letmein";
		BankUser user1 = addUser(name, birthday, password);
		BankUser user2 = addUser("Peter", "11/11/1911", "0000");
		BankAccount acc1 = addAccount(1222, AccountType.CHECKING, user1, 10000);
		BankAccount acc2 = addAccount(1234, AccountType.SAVING, user1, 10000);
		BankAccount acc3 = addAccount(3333, AccountType.CHECKING, user2, 10000);
		BankAccount acc4 = addAccount(4444, AccountType.CHECKING, user2, 10000);

		user2.addAccount(acc1);

		Teller tel1 = addTeller("Alice", "letmein", true); // this is the admin
		Teller tel2 = addTeller("BOB1", "plsletmein", false);
		Teller tel3 = addTeller("BOB2", "letmeinin", false);
	}

	public void go() {
		readLogs();

		// start logger
		try {
			logger = new BufferedWriter(new FileWriter(new File(logsFilename)));
		} catch (Exception e) {
			e.printStackTrace();
		}

		hardCodeSetUp();

		// set up thread pool
		ExecutorService threadPool = Executors.newFixedThreadPool(20);

		threadPool.execute(() -> {
			scanner = new Scanner(System.in);
			String inputCmd;

			while (scanner.hasNextLine()) {
				inputCmd = scanner.nextLine();
				if (inputCmd.equalsIgnoreCase("EXIT")) {
					System.out.println("Server is shutting down.");
					logs.add("Server is shutting down at " + new Date().toString());
					writeLogs();
					System.exit(0);
				}
			}
		});

		// create a server socket
		try (ServerSocket serverSock = new ServerSocket(50000)) {
			// print out server socket info
			System.out.println("Server is running at 127.0.0.1:50000 ...");
			logs.add("Server starts running at " + new Date().toString());

			// while true:
			while (true) {
				// accept incoming connection as a new socket
				Socket sock = serverSock.accept();
				// hand socket to client Handler
				threadPool.execute(new clientHandler(sock));

			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		// close thread pool
		threadPool.shutdown();
	}


	public void readLogs() {
		try {
			File file = new File(logsFilename);
			if (file.exists()) {
				Scanner scanner = new Scanner(file);
				String entry;

				while (scanner.hasNextLine()) {
					entry = scanner.nextLine();
					if (entry != "\n") {
						this.logs.add(entry);
					}
				}

				scanner.close();
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

	} // end method readLogs

	// write out all logs to txt
	public void writeLogs() {
		try {
			File file = new File(logsFilename);
			for (String s : logs) {
				logger.write(s);
				logger.newLine();
			}
			logger.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	} // end method writeLogs

	public BankUser addUser(String name, String birthday, String password) {
		// create new BankUser
		BankUser user = new BankUser(name, birthday, password);
		// add to HashMap userList
		userList.put(user.getId(), user);
		// add to HashMap activeUsers
		activeUsers.put(user.getId(), false);
		return user;
	}

	public BankAccount addAccount(int accountPin, AccountType accountType, BankUser user, double balance) {
		// create new BankAccount
		BankAccount account = new BankAccount(accountPin, accountType, user.getId(), balance);
		// add to HashMap accountList
		accountList.put(account.getAccountNumber(), account);
		// add to HashMap activeAccounts
		activeAccounts.put(account.getAccountNumber(), false);
		user.addAccount(account);
		return account;
	}

	public Teller addTeller(String name, String password, boolean admin) {
		// String name, String password, boolean admin
		Teller teller = new Teller(name, password, admin);
		// add to HashMap tellerList
		tellerList.put(teller.getId(), teller);
		// add to HashMap activeTellers
		activeTellers.put(teller.getId(), false);
		return teller;
	}

	public boolean checkUserLogin(int userId, String password) {
		return (userList.containsKey(userId) && userList.get(userId).getPassword().equals(password));
	}

	public boolean checkTellerLogin(int tellerId, String password) {
		return (tellerList.containsKey(tellerId) && tellerList.get(tellerId).getPassword().equals(password));
	}

	// class clientHandler
	class clientHandler implements Runnable {
		Socket sock = null;
		ObjectInputStream reader = null;
		ObjectOutputStream writer = null;

		public clientHandler(Socket sock) {
			this.sock = sock;
		}

		public void writeLogs(String s) {
			synchronized (logs) {
				logs.add(s);
			}
		}

		public boolean checkWithdraw(int userId, int accountNumber, double amount, int pin) {
			// check if it is available to withdraw
			// - accountNumber and pin matches
			// - amount < balance
			return (userList.get(userId).getAccounts().contains(accountNumber) 
					&& accountList.get(accountNumber).getAccountPin() == pin
					&& accountList.get(accountNumber).getBalance() >= amount
					&& amount > 0);
		}

		private void withdraw(int accountNumber, double amount) {
			// calculating a new balance
			double newBalance = accountList.get(accountNumber).getBalance() - amount;
			// create new account obj
			BankAccount account = accountList.get(accountNumber);
			// set the new balance to the new account obj
			account.setBalance(newBalance);
			// print
			System.out.println("new account info: " + account);
			try {
				// send new account object to client
				writer.writeUnshared(account);
			} catch (Exception e) {
				e.printStackTrace();
			}

		}

		public boolean checkDeposit(int userId, int accountNumber, double amount, int pin) {
			// check if it is available to withdraw
			// - accountNumber and pin matches
			// - amount < balance
			return (userList.get(userId).getAccounts().contains(accountNumber) 
					&& accountList.get(accountNumber).getAccountPin() == pin
					&& amount > 0);
		}

		public boolean checkUserId(int userId) {
			return userList.containsKey(userId) && !activeUsers.get(userId);
		}

		private void deposit(int accountNumber, double amount) {
			// calculating a new balance
			double newBalance = accountList.get(accountNumber).getBalance() + amount;
			// create new account obj
			BankAccount account = accountList.get(accountNumber);
			// set the new balance to the new account obj
			account.setBalance(newBalance);
			// print
			System.out.println("new account info: " + account);
			try {
				// send new account object to client
				writer.writeUnshared(account);

			} catch (Exception e) {
				e.printStackTrace();
			}

		}

		public boolean checkTransfer(int userId, int fromAccountNumber, int toAccountNumber, double amount, int pin) {
			// if account number and pin matches and amount <= balance
			return (userList.get(userId).getAccounts().contains(fromAccountNumber) 
					&& accountList.get(fromAccountNumber).getAccountPin() == pin
					&& accountList.get(fromAccountNumber).getBalance() >= amount
					&& amount > 0
					&& fromAccountNumber != toAccountNumber);
		}

		private void transfer(int fromAccountNumber, int toAccountNumber, double amount, int currUserID) {
			BankAccount account = accountList.get(fromAccountNumber);
			double newBalance = account.getBalance() - amount;
			account.setBalance(newBalance);
			// print
			System.out.println("new account info: " + account);
			try {
				// send new account object to client
				writer.writeUnshared(account);
			} catch (Exception e) {
				e.printStackTrace();
			}

			// update recipient account
			BankAccount recipientAccount = accountList.get(toAccountNumber);
			double recipientNewBalance = recipientAccount.getBalance() + amount;
			recipientAccount.setBalance(recipientNewBalance);
			// send recipient account to the client
			// if recipient account contains
			if (recipientAccount.getUsers().contains(currUserID)) {
				try {
					writer.writeUnshared(recipientAccount);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			System.out.println("new recipient account info: " + recipientAccount);
		}

		private int atmLogin() {
			Object obj;
			int currUserId = 0;
			try {

				while (true) {
					obj = reader.readObject();
					if (obj instanceof LoginMessage) {
						LoginMessage loginMessage = (LoginMessage) obj;
						LoginMessage loginReceipt;
						currUserId = loginMessage.getUserId();
						String password = loginMessage.getPassword();
						
						synchronized (activeUsers) {
							
							if (checkUserLogin(currUserId, password) && !activeUsers.get(currUserId)) {
								// LoginMessage(int id, String to, String from, Status status, String text)
								loginReceipt = new LoginMessage(Status.SUCCESS);
								writer.writeUnshared(loginReceipt); // send loginReceipt
								
								// Update activeUsers
								activeUsers.replace(currUserId, true);	
								
								writer.writeUnshared(userList.get(currUserId)); // send BankUser object to client
								System.out.println("ATM client logged in with user: " + userList.get(currUserId).getName());
								writeLogs(String.format("ATM: BankUser %s(%d) logged in at %s. %s", 
										userList.get(currUserId).getName(), currUserId, new Date().toString(), sock.getRemoteSocketAddress()));
								break;
							} else { // fail to login
								// return error message
								loginReceipt = new LoginMessage(Status.ERROR);
								writer.writeUnshared(loginReceipt); // send loginReceipt
							}
						}
						
					} else if (obj instanceof ExitMessage) {
						ExitMessage msg = (ExitMessage) obj;
						if (msg.getStatus() == Status.ONGOING) {
							ExitMessage msgReceipt = new ExitMessage(Status.SUCCESS);
							writer.writeUnshared(msgReceipt);
							break; // break the while loop
						}
					}
					
				}

			} catch (Exception e) {
				e.printStackTrace();
			}

			return currUserId;
		}

		private void closeActiveAccounts(int userId) {
			if (userList.containsKey(userId)) {
				List<Integer> accounts = userList.get(userId).getAccounts();

				synchronized (activeAccounts) {
					for (int accountNumber : accounts) {
						activeAccounts.replace(accountNumber, false);
					}
				}
			}
			synchronized (activeUsers) {
				activeUsers.replace(userId, false);
			}
		}


		private void atmHandler() {
			int currUserId = 0;
			Object obj;
			currUserId = atmLogin();
			if (currUserId == 0) {
				return;
			}
			boolean flag = false;
			
			try {
				// Wait for Account Message
				while (!flag && (obj = reader.readObject()) != null) {

					if (obj instanceof LoginMessage) {
						LoginMessage msg = (LoginMessage) obj;
						// ignore this message

					} else if (obj instanceof LogoutMessage) {
						LogoutMessage msg = (LogoutMessage) obj;
						LogoutMessage logoutReceipt = new LogoutMessage(Status.SUCCESS);
						writer.writeUnshared(logoutReceipt);

						// CLOSE ACTIVE ACCOUNTS
						closeActiveAccounts(currUserId);

						writeLogs(String.format("ATM: BankUser %s(%d) logged out at %s. %s", 
								userList.get(currUserId).getName(), currUserId, new Date().toString(), sock.getRemoteSocketAddress()));
						currUserId = 0;
						
						flag = true;
						atmHandler(); // handle new login

					} else if (obj instanceof DepositMessage) {
						// code goes here
						DepositMessage msg = (DepositMessage) obj;
						int accountNumber = msg.getAccountNumner();
						double amount = msg.getDepositAmount();
						int pin = msg.getPin();
						// if it is available to deposit
						if (checkDeposit(currUserId, accountNumber, amount, pin)) {
							writer.writeUnshared(new DepositMessage(Status.SUCCESS));
							// deposit
							deposit(accountNumber, amount);
							writeLogs(String.format("ATM: BankUser %s(%d) has deposited %.2f to Account %d at %s.", 
									userList.get(currUserId).getName(), currUserId, amount, accountNumber, new Date().toString()));

						} else { // is it is not available to deposit
							// return error message
							writer.writeUnshared(new DepositMessage(Status.ERROR));
						}

					} else if (obj instanceof WithdrawMessage) {
						WithdrawMessage msg = (WithdrawMessage) obj;
						// code goes here
						int accountNumber = msg.getAccountNumber();
						double amount = msg.getWithdrawAmount();
						int pin = msg.getPin();

						if (checkWithdraw(currUserId, accountNumber, amount, pin)) { // if it is available to withdraw
							// send back success message
							writer.writeUnshared(new WithdrawMessage(Status.SUCCESS));
							// withdraw
							withdraw(accountNumber, amount);
							writeLogs(String.format("ATM: BankUser %s(%d) has withdrawn %.2f to Account %d at %s.", 
									userList.get(currUserId).getName(), currUserId, amount, accountNumber, new Date().toString()));

						} else { // if it is not available to withdraw
							// return error message
							writer.writeUnshared(new WithdrawMessage(Status.ERROR));
						}

					} else if (obj instanceof TransferMessage) {
						TransferMessage msg = (TransferMessage) obj;
						int fromAccountNumber = msg.getFromAccountNumber();
						int toAccountNumber = msg.getToAccountNumber();
						double transferAmount = msg.getTransferAmount();
						int pin = msg.getPin();

						TransferMessage msgReceipt;
						if (msg.getStatus() == Status.ONGOING
								&& checkTransfer(currUserId, fromAccountNumber, toAccountNumber, transferAmount, pin)) {
							msgReceipt = new TransferMessage(Status.SUCCESS, fromAccountNumber, toAccountNumber,
									transferAmount);
							writer.writeUnshared(msgReceipt);

							transfer(fromAccountNumber, toAccountNumber, transferAmount, currUserId);
							writeLogs(String.format("ATM: BankUser %s(%d) has transferred %.2f from Account %d to Account %d at %s.", 
									userList.get(currUserId).getName(), currUserId, transferAmount, fromAccountNumber, toAccountNumber, new Date().toString()));

						} else {
							msgReceipt = new TransferMessage(Status.ERROR, fromAccountNumber, toAccountNumber,
									transferAmount);
							writer.writeUnshared(msgReceipt);
						}

					} else if (obj instanceof AccountMessage) {
						AccountMessage msg = (AccountMessage) obj;
						// code goes here
						currUserId = msg.getCurrUserId();
						int accountNumber = msg.getAccountNumber();
						synchronized (activeAccounts) {
							if (accountList.get(accountNumber).getUsers().contains(currUserId)
									&& activeAccounts.get(accountNumber) == false) {
								activeAccounts.replace(accountNumber, true);
								// int id, int currUserId, int accountNumber, Status status
								AccountMessage msgReceipt = new AccountMessage(currUserId, accountNumber,
										Status.SUCCESS);
								writer.writeUnshared(msgReceipt);
								writer.writeUnshared(accountList.get(accountNumber));
							} else { // Invalid request
								AccountMessage msgReceipt = new AccountMessage(currUserId, accountNumber, Status.ERROR);
								writer.writeUnshared(msgReceipt);
							}
						}

					} else if (obj instanceof AccountInfoMessage) {
						AccountInfoMessage msg = (AccountInfoMessage) obj;
						int accountNumber = msg.getAccountNumber();
						AccountInfoMessage msgReceipt;
						if (msg.getStatus() == Status.ONGOING && accountList.containsKey(accountNumber)) {
							msgReceipt = new AccountInfoMessage(Status.SUCCESS, accountNumber,
									accountList.get(accountNumber).getUsers());
						} else {
							msgReceipt = new AccountInfoMessage(Status.ERROR, accountNumber);
						}
						writer.writeUnshared(msgReceipt);

					} else if (obj instanceof UserInfoMessage) {
						UserInfoMessage msg = (UserInfoMessage) obj;
						int userId = msg.getUserID();
						UserInfoMessage msgReceipt;
						if (msg.getStatus() == Status.ONGOING && userList.containsKey(userId)) {
							msgReceipt = new UserInfoMessage(Status.SUCCESS, userId, userList.get(userId).getName());
						} else {
							msgReceipt = new UserInfoMessage(Status.ERROR, userId);
						}
						writer.writeUnshared(msgReceipt);
					} else {
						// ignore the message
					}

				} // end while loop

			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				// housekeeping
				if (currUserId > 0 && userList.containsKey(currUserId)) {
					// CLOSE ACTIVE ACCOUNTS
					closeActiveAccounts(currUserId);
					writeLogs(String.format("ATM: BankUser %s(%d) logged out at %s. %s", 
							userList.get(currUserId).getName(), currUserId, new Date().toString(), sock.getRemoteSocketAddress()));
				}
			}

		} // end method atmHandler

		private int tellerLogin() {
			Object obj;
			int tellerId = 0;

			try {
				// handle Teller login
				while (true) {
					obj = reader.readObject();
					if (obj instanceof LoginMessage) {

						LoginMessage loginMessage = (LoginMessage) obj;
						LoginMessage loginReceipt;
						tellerId = loginMessage.getUserId();
						String password = loginMessage.getPassword();


						// if user exists and password is correct, return success message, user info and
						// break
						synchronized (activeTellers) {
							if (checkTellerLogin(tellerId, password) && !activeTellers.get(tellerId)) {
								// LoginMessage(int id, String to, String from, Status status, String text)
								loginReceipt = new LoginMessage(Status.SUCCESS);
								writer.writeUnshared(loginReceipt); // send loginReceipt

								activeTellers.replace(tellerId, true);

								writer.writeUnshared(tellerList.get(tellerId)); // send BankUser object to client
								System.out.println("Teller client logged in with teller: " + tellerList.get(tellerId).getName());
								writeLogs(String.format("Teller: teller %s(%d) logged in at %s.", tellerList.get(tellerId).getName(), tellerId, new Date().toString()));
								break;
							} else { // fail to login
								// return error message
								loginReceipt = new LoginMessage(Status.ERROR);
								writer.writeUnshared(loginReceipt); // send loginReceipt
							}

						}

					} else if (obj instanceof ExitMessage) {
						ExitMessage msg = (ExitMessage) obj;
						if (msg.getStatus() == Status.ONGOING) {
							ExitMessage msgReceipt = new ExitMessage(Status.SUCCESS);
							writer.writeUnshared(msgReceipt);
							break; // break the while loop
						}

					} 

				} // end while loop
			} catch (Exception e) {
				e.printStackTrace();
			}

			return tellerId;
		}

		private void tellerHandler() {
			Object obj;
			int tellerId = tellerLogin();
			if (tellerId == 0) {
				return;
			}
			int userId = 0;
			boolean flag = false;
			try {
				// handle remaining messages
				while (!flag && (obj = reader.readObject()) != null) {

					if (obj instanceof LoginMessage) {
						LoginMessage msg = (LoginMessage) obj;
						// ignore this message

					} else if (obj instanceof LogoutMessage) {
						LogoutMessage msg = (LogoutMessage) obj;
						LogoutMessageType type = msg.getType();						
						System.out.println("Logout msg received.");
						switch (type) {
						case BANK_USER: { // log out bank user

							// CLOSE ACTIVE ACCOUNTS
							closeActiveAccounts(userId);
							LogoutMessage logoutReceipt = new LogoutMessage(Status.SUCCESS);
							writer.writeUnshared(logoutReceipt);
							
							writeLogs(String.format("Teller: teller %s(%d) logged out BankUser %s(%d) at %s.", 
									tellerList.get(tellerId).getName(), tellerId, userList.get(userId).getName(), userId, new Date().toString()));
							userId = 0;
							break;
						}
						case TELLER: { // log out teller
							flag = true;
							// code goes here
							synchronized(activeTellers) {
								activeTellers.replace(tellerId, false);
							}
							LogoutMessage logoutReceipt = new LogoutMessage(Status.SUCCESS);
							writer.writeUnshared(logoutReceipt);
							
							writeLogs(String.format("Teller: teller %s(%d) logged out at %s.", tellerList.get(tellerId).getName(), tellerId, new Date().toString()));
							
							tellerId = 0;
							tellerHandler();
							break;
						}
						}

					} else if (obj instanceof DepositMessage) {
						DepositMessage msg = (DepositMessage) obj;
						// code goes here
						int accountNumber = msg.getAccountNumner();
						double amount = msg.getDepositAmount();
						int pin = msg.getPin();
						// if it is available to deposit
						if (checkDeposit(userId, accountNumber, amount, pin)) {
							writer.writeUnshared(new DepositMessage(Status.SUCCESS));
							// deposit
							deposit(accountNumber, amount);
							writeLogs(String.format("Teller: Teller %s(%d) assisted BankUser %s(%d) with depositing %.2f to Account %d at %s.", 
									tellerList.get(tellerId).getName(), tellerId, userList.get(userId).getName(), userId, amount, accountNumber, new Date().toString()));

						} else { // is it is not available to deposit
							// return error message
							writer.writeUnshared(new DepositMessage(Status.ERROR));
						}

					} else if (obj instanceof WithdrawMessage) {
						WithdrawMessage msg = (WithdrawMessage) obj;
						// code goes here
						int accountNumber = msg.getAccountNumber();
						double amount = msg.getWithdrawAmount();
						int pin = msg.getPin();
						if (checkWithdraw(userId, accountNumber, amount, pin)) { // if it is available to withdraw
							// send back success message
							writer.writeUnshared(new WithdrawMessage(Status.SUCCESS));
							// withdraw
							withdraw(accountNumber, amount);
							writeLogs(String.format("Teller: Teller %s(%d) assisted BankUser %s(%d) with withdrawing %.2f to Account %d at %s.", 
									tellerList.get(tellerId).getName(), tellerId, userList.get(userId).getName(), userId, amount, accountNumber, new Date().toString()));

						} else { // if it is not available to withdraw
							// return error message
							writer.writeUnshared(new WithdrawMessage(Status.ERROR));
						}

					} else if (obj instanceof TransferMessage) {
						TransferMessage msg = (TransferMessage) obj;
						// code goes here
						int fromAccountNumber = msg.getFromAccountNumber();
						int toAccountNumber = msg.getToAccountNumber();
						double transferAmount = msg.getTransferAmount();
						int pin = msg.getPin();

						TransferMessage msgReceipt;

						if (msg.getStatus() == Status.ONGOING
								&& checkTransfer(userId, fromAccountNumber, toAccountNumber, transferAmount, pin)) {
							msgReceipt = new TransferMessage(Status.SUCCESS, fromAccountNumber, toAccountNumber,
									transferAmount);
							writer.writeUnshared(msgReceipt);

							transfer(fromAccountNumber, toAccountNumber, transferAmount, userId);
							writeLogs(String.format("Teller: Teller %s(%d) assisted BankUser %s(%d) with transferring %.2f from Account %d to Account %d at %s.", 
									tellerList.get(tellerId).getName(), tellerId, userList.get(userId).getName(), userId, transferAmount, fromAccountNumber, toAccountNumber, new Date().toString()));

						} else {
							msgReceipt = new TransferMessage(Status.ERROR, fromAccountNumber, toAccountNumber,
									transferAmount);
							writer.writeUnshared(msgReceipt);
						}
					} else if (obj instanceof AccountMessage) {
						AccountMessage msg = (AccountMessage) obj;
						// code goes here
						AccountMessageType type = msg.getType();
						AccountMessage msgReceipt;
						Map<String, String> info = msg.getInfo();
						switch (type) {
						case ADD_USER: {
							// reply with success status
							msgReceipt = new AccountMessage(Status.SUCCESS, AccountMessageType.ADD_USER);
							// send back to client
							writer.writeUnshared(msgReceipt);

							// create new BankUser
							String name = info.get("name");
							String birthday = info.get("birthday");
							String password = info .get("password");
							BankUser newUser = addUser(name, birthday, password);

							writeLogs(String.format("Teller: Teller %s(%d) created a new BankUser %s(%d) at %s", 
									tellerList.get(tellerId).getName(), tellerId, newUser.getName(), newUser.getId(), new Date().toString()));

							// send BankUser to client
							writer.writeUnshared(newUser);
							
							// log in as new BankUser
							userId = newUser.getId();
							
							writeLogs(String.format("Teller: teller %s(%d) logged in BankUser %s(%d) at %s.", 
									tellerList.get(tellerId).getName(), tellerId, userList.get(userId).getName(), userId, new Date().toString()));
							
							break;
						}
						case USER_INFO: { // log in as BankUser
							// check if user id is valid
							userId = Integer.parseInt( msg.getInfo().get("userId"));
							synchronized (activeUsers) {
								if (checkUserId(userId) && !activeUsers.get(userId)) {
									// reply with success status and send back to client
									msgReceipt = new AccountMessage(Status.SUCCESS, AccountMessageType.USER_INFO);
									writer.writeUnshared(msgReceipt);

									activeUsers.replace(userId, true);

									// send back BankUser obj
									writer.writeUnshared(userList.get(userId));
									
									writeLogs(String.format("Teller: teller %s(%d) logged in BankUser %s(%d) at %s.", 
											tellerList.get(tellerId).getName(), tellerId, userList.get(userId).getName(), userId, new Date().toString()));
								} else {
									// reply with ERROR status and send back to client
									msgReceipt = new AccountMessage(Status.ERROR, AccountMessageType.USER_INFO);
									writer.writeUnshared(msgReceipt);
								}
							}

							break;
						}
						case ACCOUNT_INFO:{
							int accountNumber = msg.getAccountNumber();
							synchronized (activeAccounts) {
								if (accountList.get(accountNumber).getUsers().contains(userId)) {
									activeAccounts.replace(accountNumber, true);
									// int id, int currUserId, int accountNumber, Status status
									msgReceipt = new AccountMessage(userId, accountNumber,
											Status.SUCCESS);
									writer.writeUnshared(msgReceipt);
									// send BankAccount obj
									writer.writeUnshared(accountList.get(accountNumber));
								} else { // Invalid request
									msgReceipt = new AccountMessage(userId, accountNumber, Status.ERROR);
									writer.writeUnshared(msgReceipt);
								}
							}
							break;
						}
						case ADD_ACCOUNT: {
							AccountType accountType = AccountType.valueOf(info.get("accountType"));
							int pin = Integer.parseInt(info.get("pin"));

							// create a new BankAccount for user
							BankAccount bankAccount = new BankAccount(pin, accountType, userId);
							int accountNumber = bankAccount.getAccountNumber();
							synchronized (accountList) {
								accountList.put(accountNumber, bankAccount);
							}
							synchronized (activeAccounts) {
								activeAccounts.put(accountNumber, false);
							}
							userList.get(userId).addAccount(bankAccount);

							// reply with success status with new accountNumber
							Map<String, String> newInfo = new HashMap<>();
							newInfo.put("accountNumber", Integer.toString(accountNumber));
							msgReceipt = new AccountMessage(Status.SUCCESS, AccountMessageType.ADD_ACCOUNT, newInfo);

							writer.writeUnshared(msgReceipt);
							writeLogs(String.format("Teller: Teller %s(%d) assisted BankUser %s(%d) with opening a new Account %d at %s.", 
									tellerList.get(tellerId).getName(), tellerId, userList.get(userId).getName(), userId, accountNumber, new Date().toString()));

							break;
						}
						case REM_ACCOUNT: {
							int accountNumber = Integer.parseInt(info.get("accountNumber"));

							// check if it is ready to remove account
							// - if this user belongs to this account
							// - if this account has balance of zero
							// - if this user is the admin of this account
							if (accountList.get(accountNumber).getUsers().contains(userId)
									&& accountList.get(accountNumber).getBalance() == 0
									&& accountList.get(accountNumber).getAdminID() == userId) {

								// update BankUser in userList, 
								userList.get(userId).getAccounts().remove(Integer.valueOf(accountNumber));

								// update activeAccounts, accountList
								synchronized (activeAccounts) {
									activeAccounts.remove(accountNumber);
								}
								accountList.remove(accountNumber);

								// send back msgReceipt
								msgReceipt = new AccountMessage(Status.SUCCESS, AccountMessageType.REM_ACCOUNT);
								// send back msgReceipt
								writer.writeUnshared(msgReceipt);
								writeLogs(String.format("Teller: Teller %s(%d) assisted BankUser %s(%d) with removing an Account %d. at %s", 
										tellerList.get(tellerId).getName(), tellerId, userList.get(userId).getName(), userId, accountNumber, new Date().toString()));

							} else { // not able to remove
								msgReceipt = new AccountMessage(Status.ERROR, AccountMessageType.REM_ACCOUNT);
								// send back msgReceipt
								writer.writeUnshared(msgReceipt);
							}
							break;
						}
						case CHG_PWD: {
							String birthday = info.get("birthday");
							String password = info.get("password");
							BankUser currUser = userList.get(userId);
							// check if birthday is correct
							if (currUser.getBirthday().equals(birthday)) {
								// if yes, update password in userList, BankUser
								currUser.setPassword(password);

								// send back success message
								msgReceipt = new AccountMessage(Status.SUCCESS, AccountMessageType.CHG_PWD);
								writer.writeUnshared(msgReceipt);
								writeLogs(String.format("Teller: Teller %s(%d) assisted BankUser %s(%d) with changing password at %s.", 
										tellerList.get(tellerId).getName(), tellerId, userList.get(userId).getName(), userId, new Date().toString()));

							} else { // incorrect birthday, fail to change password

								msgReceipt = new AccountMessage(Status.ERROR, AccountMessageType.CHG_PWD);
								// send back msgReceipt
								writer.writeUnshared(msgReceipt);
							}
							break;
						}
						case CHG_PIN: {
							int tempUserId = Integer.parseInt(info.get("userId"));
							int accountNumber = Integer.parseInt(info.get("accountNumber"));
							int pin = Integer.parseInt(info.get("pin"));

							if (tempUserId == userId && accountList.get(accountNumber).getUsers().contains(userId)) {

								// update pin on server
								accountList.get(accountNumber).setAccountPin(pin);

								// send back success message
								msgReceipt = new AccountMessage(Status.SUCCESS, AccountMessageType.CHG_PIN);
								writer.writeUnshared(msgReceipt);
								writeLogs(String.format("Teller: Teller %s(%d) assisted BankUser %s(%d) with changing pin for Account %d at %s.", 
										tellerList.get(tellerId).getName(), tellerId, userList.get(userId).getName(), userId, accountNumber, new Date().toString()));

							} else { // fail to change pin

								msgReceipt = new AccountMessage(Status.ERROR, AccountMessageType.CHG_PIN);
								// send back msgReceipt
								writer.writeUnshared(msgReceipt);

							}
							break;
						}
						case TXF_ADMIN: {
							int tempUserId = Integer.parseInt(info.get("userId"));
							int accountNumber = Integer.parseInt(info.get("accountNumber"));
							int pin = Integer.parseInt(info.get("pin"));
							int recipientId = Integer.parseInt(info.get("recipientId"));

							// check if it is ready to transfer admin
							// - if it is initiated by curr user
							// - if the recipient is other user linked to this account
							// - if this account's admin is curr user
							// - if this account belongs to curr user
							// - if this account belongs to recipient user
							// - if this account pin matches with input pin
							if (tempUserId == userId && recipientId != userId
									&& accountList.get(accountNumber).getAdminID() == userId
									&& accountList.get(accountNumber).getUsers().contains(userId)
									&& accountList.get(accountNumber).getUsers().contains(recipientId)
									&& accountList.get(accountNumber).getAccountPin() == pin) {

								// update account admin in account on server
								accountList.get(accountNumber).setAdminID(recipientId);

								// send back success message
								msgReceipt = new AccountMessage(Status.SUCCESS, AccountMessageType.TXF_ADMIN);
								writer.writeUnshared(msgReceipt);
								writeLogs(String.format("Teller: Teller %s(%d) assisted BankUser %s(%d) with transferring admin for Account %d from BankUser %d to BankUser %d at %s.", 
										tellerList.get(tellerId).getName(), tellerId, userList.get(userId).getName(), userId, accountNumber, userId, recipientId, new Date().toString()));

							} else { // fail to transfer admin

								msgReceipt = new AccountMessage(Status.ERROR, AccountMessageType.TXF_ADMIN);
								// send back msgReceipt
								writer.writeUnshared(msgReceipt);

							}

							break;
						}
						case CHK_OWN: {
							int tempUserId = Integer.parseInt(info.get("userId"));
							int accountNumber = Integer.parseInt(info.get("accountNumber"));

							if (accountList.get(accountNumber).getUsers().contains(tempUserId)) {
								msgReceipt = new AccountMessage(Status.SUCCESS, AccountMessageType.CHK_OWN);
								writer.writeUnshared(msgReceipt);
							} else {
								msgReceipt = new AccountMessage(Status.ERROR, AccountMessageType.CHK_OWN);
								// send back msgReceipt
								writer.writeUnshared(msgReceipt);
							}
							break;
						}
						case CHK_DUP: {
							int tempUserId = Integer.parseInt(info.get("userId"));
							int accountNumber = Integer.parseInt(info.get("accountNumber"));
							int addUserId = Integer.parseInt(info.get("checkId"));

							if (accountList.get(accountNumber).getUsers().contains(addUserId)) {
								msgReceipt = new AccountMessage(Status.ERROR, AccountMessageType.CHK_DUP);
								writer.writeUnshared(msgReceipt);
							} else {
								msgReceipt = new AccountMessage(Status.SUCCESS, AccountMessageType.CHK_DUP);
								// send back msgReceipt
								writer.writeUnshared(msgReceipt);
							}
							break;
						}
						case CHK_ITSELF: {
							int loggedInUserId = Integer.parseInt(info.get("userId"));
							int accountNumber = Integer.parseInt(info.get("accountNumber"));
							int remUserId = Integer.parseInt(info.get("checkId"));

							if (loggedInUserId != remUserId) {
								msgReceipt = new AccountMessage(Status.SUCCESS, AccountMessageType.CHK_ITSELF);
								writer.writeUnshared(msgReceipt);
							} else {
								msgReceipt = new AccountMessage(Status.ERROR, AccountMessageType.CHK_ITSELF);
								// send back msgReceipt
								writer.writeUnshared(msgReceipt);
							}
							break;
						}
						case ADD_USER_TO_ACC: {
							int tempUserId = Integer.parseInt(info.get("userId"));
							Integer userIdToAdd = Integer.valueOf(tempUserId);
							int accountNumber = Integer.parseInt(info.get("accountNumber"));
							Integer accNumToAdd = Integer.valueOf(accountNumber);
							accountList.get(accountNumber).getUsers().add(userIdToAdd);
							userList.get(tempUserId).getAccounts().add(accNumToAdd);

							BankUser newUser = userList.get(tempUserId);
							writer.writeUnshared(newUser);

							writeLogs(String.format("Teller: Teller %s(%d) assisted BankUser %s(%d) with adding BankUser %d to Account %d at %s.", 
									tellerList.get(tellerId).getName(), tellerId, userList.get(userId).getName(), userId, userIdToAdd, accountNumber, new Date().toString()));

							break;
						}
						case REM_USER_FROM_ACC: {
							int tempUserId = Integer.parseInt(info.get("userId"));
							Integer userIdToRemove = Integer.valueOf(tempUserId);
							int accountNumber = Integer.parseInt(info.get("accountNumber"));
							Integer accNumToRem = Integer.valueOf(accountNumber);
							accountList.get(accountNumber).getUsers().remove(userIdToRemove);
							userList.get(tempUserId).getAccounts().remove(accNumToRem);
							BankUser remUser = userList.get(tempUserId);
							System.out.println("Sending BankUser object to client: " + remUser);
							writer.writeUnshared(remUser);

							writeLogs(String.format("Teller: Teller %s(%d) assisted BankUser %s(%d) with removing BankUser %d from Account %d at %s.", 
									tellerList.get(tellerId).getName(), tellerId, userList.get(userId).getName(), userId, userIdToRemove, accountNumber, new Date().toString()));

							break;
						}
						case CHK_ACC_ADM: {
							int tempUserId = Integer.parseInt(info.get("userId"));
							Integer tempIdObj = Integer.valueOf(tempUserId);
							int accountNumber = Integer.parseInt(info.get("accountNumber"));
							Integer accNumObj = Integer.valueOf(accountNumber);
							if (accountList.get(accNumObj).getAdminID() == tempIdObj) {
								msgReceipt = new AccountMessage(Status.SUCCESS, AccountMessageType.CHK_ACC_ADM);
								writer.writeUnshared(msgReceipt);
								System.out.println("CHK_ACC_ADM SUCCESS msg sent.");
							} else {
								msgReceipt = new AccountMessage(Status.ERROR, AccountMessageType.CHK_ACC_ADM);
								writer.writeUnshared(msgReceipt);
								System.out.println("CHK_ACC_ADM ERROR msg sent.");
							}


							break;
						}
						default: break;
						}

					} else if (obj instanceof AccountInfoMessage) {
						AccountInfoMessage msg = (AccountInfoMessage) obj;
						int accountNumber = msg.getAccountNumber();
						AccountInfoMessage msgReceipt;
						if (msg.getStatus() == Status.ONGOING && accountList.containsKey(accountNumber)) {
							msgReceipt = new AccountInfoMessage(Status.SUCCESS, accountNumber,
									accountList.get(accountNumber).getUsers());
						} else {
							msgReceipt = new AccountInfoMessage(Status.ERROR, accountNumber);
						}
						writer.writeUnshared(msgReceipt);

					} else if (obj instanceof UserInfoMessage) {
						UserInfoMessage msg = (UserInfoMessage) obj;
						int toUserId = msg.getUserID();
						UserInfoMessage msgReceipt;
						if (msg.getStatus() == Status.ONGOING && userList.containsKey(toUserId)) {
							msgReceipt = new UserInfoMessage(Status.SUCCESS, toUserId, userList.get(toUserId).getName());
						} else {
							msgReceipt = new UserInfoMessage(Status.ERROR, toUserId);
						}
						writer.writeUnshared(msgReceipt);

					} else if (obj instanceof TellerMessage) {
						TellerMessage msg = (TellerMessage) obj;
						// code goes here
						TellerMessageType type = msg.getType();
						TellerMessage msgReceipt;
						Map<String, String> info = msg.getInfo();

						switch (type) {
						case ADD_TELLER: {
							String name = info.get("name");
							String password = info.get("password");

							Teller newTeller = new Teller(name, password, false);
							int newTellerId = newTeller.getId();
							synchronized (tellerList) {
								tellerList.put(newTellerId, newTeller);
							}
							synchronized (activeTellers) {
								activeTellers.put(newTellerId, false);
							}

							// send back msgReceipt
							msgReceipt = new TellerMessage(Status.SUCCESS, TellerMessageType.ADD_TELLER);
							writer.writeUnshared(msgReceipt);

							// send Teller obj to client
							writer.writeUnshared(newTeller);

							writeLogs(String.format("Teller: Teller %s(%d) created a new Teller %s(%d) at %s.", 
									tellerList.get(tellerId).getName(), tellerId, name, newTellerId, new Date().toString()));

							break;
						}
						case REM_TELLER: {
							int tempTellerId = Integer.parseInt(info.get("tellerId"));
							// check if tempTellerId is valid or not and this teller is not logged in

							if (tellerList.containsKey(tempTellerId) && activeTellers.get(tempTellerId) == false) {

								String name = tellerList.get(tempTellerId).getName();

								// remove teller from tellerList and activeTellers
								synchronized (tellerList) {
									tellerList.remove(tempTellerId);
								}
								synchronized (activeTellers) {
									activeTellers.remove(tempTellerId);
								}

								// send back msgReceipt with success status
								msgReceipt = new TellerMessage(Status.SUCCESS, TellerMessageType.REM_TELLER);
								writer.writeUnshared(msgReceipt);

								writeLogs(String.format("Teller: Teller %s(%d) removed the Teller %s(%d) at %s.", 
										tellerList.get(tellerId).getName(), tellerId, name, tempTellerId, new Date().toString()));

							} else {
								// send back msgReceipt with ERROR status
								msgReceipt = new TellerMessage(Status.ERROR, TellerMessageType.REM_TELLER);
								writer.writeUnshared(msgReceipt);

							}

							break;
						}
						case VIEW_LOGS: {

							msgReceipt = new TellerMessage(Status.SUCCESS, logs);
							writer.reset(); // reset object reference to logs
							writer.writeUnshared(msgReceipt);
							break;
							
						}
						case TELLERS_INFO: {
							// if this is admin teller
							if (msg.getStatus() == Status.ONGOING && tellerList.get(tellerId).getAdmin()) {

								Map<String, String> newInfo = new HashMap<>();
								for (int tempTellerId: tellerList.keySet()) {
									newInfo.put(Integer.toString(tempTellerId), tellerList.get(tempTellerId).getName());
								}

								// send back msgReceipt with SUCCESS status
								msgReceipt = new TellerMessage(Status.SUCCESS, TellerMessageType.TELLERS_INFO, newInfo);
								writer.writeUnshared(msgReceipt);

							} else {
								// send back msgReceipt with ERROR status
								msgReceipt = new TellerMessage(Status.ERROR, TellerMessageType.TELLERS_INFO);
								writer.writeUnshared(msgReceipt);
							}

							break;
						}
						default: break;	
						} // end switch statement
					}
				} // end while loop

			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				// housekeeping
				if (tellerId > 0 && activeTellers.containsKey(tellerId)) {
					synchronized(activeTellers) {
						activeTellers.replace(tellerId, false);
					}
					writeLogs(String.format("Teller: teller %s(%d) logged out at %s.", tellerList.get(tellerId).getName(), tellerId, new Date().toString()));
				}
				
				if (userId > 0 && activeUsers.containsKey(userId)) {
					// CLOSE ACTIVE ACCOUNTS
					if (userId != 0) {
						closeActiveAccounts(userId);
						userId = 0;
					}
					if (tellerId != 0) {
						writeLogs(String.format("Teller: teller %s(%d) logged out BankUser %s(%d) at %s.", 
								tellerList.get(tellerId).getName(), tellerId, userList.get(userId).getName(), userId, new Date().toString()));
					}
					
				}
			}

		} // end method tellerHandler

		private HelloMessage handshake() {
			Object obj = null;
			HelloMessage clientHello = null;
			try {
				// receive hello messages and recognize which client (ATM/Teller) it's connected
				// to
				obj = reader.readObject();
				if (obj instanceof HelloMessage) {
					clientHello = (HelloMessage) obj;
					System.out.println(clientHello.toString());
					HelloMessage serverHello = new HelloMessage("Server", Status.SUCCESS);
					writer.writeUnshared(serverHello);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			return clientHello;

		}

		public void run() {
			// print out connection info
			System.out.println("A client is connected: " + this.sock);

			try {
				reader = new ObjectInputStream(sock.getInputStream());
				writer = new ObjectOutputStream(sock.getOutputStream());

				HelloMessage clientHello = handshake(); // handshake with client

				if (clientHello.getFrom().equals("ATM")) { // if it's from ATM, hand it to ATM handler
					System.out.println("An ATM is connected: " + this.sock);
					atmHandler();
					System.out.println("An ATM is closed: " + this.sock);
				} else { // if it's from Teller, hand it to Teller handler
					System.out.println("A Teller is connected: " + this.sock);
					tellerHandler();
					System.out.println("A Teller is closed: " + this.sock);
				}

				// after sending logout messages to client
				// close reader
				reader.close();
				// close writer
				writer.close();

			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				try {
					// close sock
					sock.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			} // end try/catch/finally

		} // end method run

	} // end class clientHandler

	public Map<Integer, BankUser> getUserList() {
		return this.userList;
	}

	public Map<Integer, BankAccount> getAccountList() {
		return this.accountList;
	}

	public Map<Integer, Teller> getTellerList() {
		return this.tellerList;
	}

	public Map<Integer, Boolean> getActiveUsers() {
		return this.activeUsers;
	}

	public Map<Integer, Boolean> getActiveAccounts() {
		return this.activeAccounts;
	}

	public Map<Integer, Boolean> getActiveTellers() {
		return this.activeTellers;
	}

}
