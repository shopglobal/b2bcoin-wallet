package com.b2beyond.wallet.b2bcoin;

import com.b2beyond.wallet.b2bcoin.controler.CoinRpcController;
import com.b2beyond.wallet.b2bcoin.controler.DaemonController;
import com.b2beyond.wallet.b2bcoin.controler.PropertiesLoader;
import com.b2beyond.wallet.b2bcoin.controler.WalletRpcController;
import com.b2beyond.wallet.b2bcoin.daemon.DaemonPortChecker;
import com.b2beyond.wallet.b2bcoin.daemon.rpc.NoParamsRpcPoller;
import com.b2beyond.wallet.b2bcoin.daemon.rpc.RpcPoller;
import com.b2beyond.wallet.b2bcoin.daemon.rpc.TransactionItemsRpcPoller;
import com.b2beyond.wallet.b2bcoin.daemon.rpc.UnconfirmedTransactionHashesRpcPoller;
import com.b2beyond.wallet.b2bcoin.daemon.rpc.model.Addresses;
import com.b2beyond.wallet.b2bcoin.daemon.rpc.model.BlockCount;
import com.b2beyond.wallet.b2bcoin.daemon.rpc.model.Status;
import com.b2beyond.wallet.b2bcoin.util.B2BUtil;
import com.b2beyond.wallet.b2bcoin.view.TabContainer;
import com.b2beyond.wallet.b2bcoin.view.controller.ActionController;
import com.b2beyond.wallet.b2bcoin.view.controller.AddressesController;
import com.b2beyond.wallet.b2bcoin.view.controller.PaymentController;
import com.b2beyond.wallet.b2bcoin.view.controller.PoolMiningController;
import com.b2beyond.wallet.b2bcoin.view.controller.SoloMiningController;
import com.b2beyond.wallet.b2bcoin.view.view.AddressesTabView;
import com.b2beyond.wallet.b2bcoin.view.view.CreatePaymentTabView;
import com.b2beyond.wallet.b2bcoin.view.view.MainFrame;
import com.b2beyond.wallet.b2bcoin.view.view.MenuBar;
import com.b2beyond.wallet.b2bcoin.view.view.MiningTabView;
import com.b2beyond.wallet.b2bcoin.view.view.PaymentTabView;
import com.b2beyond.wallet.b2bcoin.view.view.SplashWindow;
import com.b2beyond.wallet.b2bcoin.view.view.StatusTabView;
import com.b2beyond.wallet.b2bcoin.view.view.TransactionsTabView;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.log4j.Logger;

import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import java.awt.Color;
import java.awt.SystemColor;
import java.io.IOException;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;


public class B2BWallet {

    private static Logger LOGGER = Logger.getLogger(B2BWallet.class);

    private ActionController actionController;

    public PropertiesConfiguration applicationProperties;
    public PropertiesConfiguration walletDaemonProperties;
    public PropertiesConfiguration coinDaemonProperties;

    private SplashWindow loadWindow;
    private int loadingCounter = 1;


    public static void main(String[] args) {
        new B2BWallet();
    }

    public B2BWallet() {
        if (!availableForConnection(9090) || !availableForConnection(39155) || !availableForConnection(39156)) {
            JOptionPane.showMessageDialog(null,
                    "Please quite your current b2bcoin dameon and wallet daemon, one or both of them are still running.",
                    "Fatal error",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        UIManager.put("Table.foreground",
                new ColorUIResource(Color.black));
        UIManager.put("Table.background",
                new ColorUIResource(157, 217, 210));

        if (LOGGER.isDebugEnabled()) {
            Properties p = System.getProperties();
            Enumeration keys = p.keys();
            while (keys.hasMoreElements()) {
                String key = (String)keys.nextElement();
                String value = (String)p.get(key);
                LOGGER.info(key + ": " + value);
            }
        }
        loadingFrame();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                actionController.exit();
            }
        });

        B2BUtil.copyConfigsOnFirstRun();
        loadWindow.setProgress(loadingCounter++);

        applicationProperties = new PropertiesLoader("application.config").getProperties();
        loadWindow.setProgress(loadingCounter++);
        walletDaemonProperties = new PropertiesLoader("coin-wallet.conf").getProperties();
        loadWindow.setProgress(loadingCounter++);
        coinDaemonProperties = new PropertiesLoader("coin.conf").getProperties();
        loadWindow.setProgress(loadingCounter++);

        LOGGER.info("Properties loaded, wallet can get started");
        String daemonExecutable = applicationProperties.getString("coin-daemon-" + B2BUtil.getOperatingSystem());
        String walletExecutable = applicationProperties.getString("wallet-daemon-" + B2BUtil.getOperatingSystem());
        String poolMinerExecutable = applicationProperties.getString("pool-miner-daemon-" + B2BUtil.getOperatingSystem());
        B2BUtil.copyDaemonsOnFirstRun(daemonExecutable, walletExecutable, poolMinerExecutable);

        DaemonController coinDaemon = new DaemonController(
                applicationProperties,
                walletDaemonProperties,
                B2BUtil.getOperatingSystem());
        WalletRpcController walletRpcController = new WalletRpcController(applicationProperties);
        CoinRpcController coinRpcController = new CoinRpcController(applicationProperties);
        actionController = new ActionController(coinDaemon, walletRpcController, coinRpcController);
        loadWindow.setProgress(loadingCounter++);

        new Thread(new DaemonPortChecker()).start();

        LOGGER.info("Starting controllers ...");
        AddressesController addressesController = new AddressesController(
                walletRpcController.getCreateAddressExecutor(),
                walletRpcController.getBalanceExecutor());
        PaymentController paymentController = new PaymentController(
                walletRpcController.getPaymentExecutor());
        PoolMiningController miningController = new PoolMiningController(
                applicationProperties,
                B2BUtil.getOperatingSystem());
        SoloMiningController soloMiningController = new SoloMiningController(
                applicationProperties,
                walletDaemonProperties,
                B2BUtil.getOperatingSystem());
        loadWindow.setProgress(loadingCounter++);
        LOGGER.info("Controllers started.");

        LOGGER.info("Creating tab view instances ...");
        StatusTabView statusTabView = new StatusTabView(actionController, walletRpcController.getResetExecutor(), walletRpcController.getTransactionExecutor());
        TransactionsTabView transactionsTabView = new TransactionsTabView(walletRpcController.getTransactionExecutor());
        AddressesTabView addressesTabView = new AddressesTabView(addressesController);
        PaymentTabView paymentTabView = new PaymentTabView();
        CreatePaymentTabView createPaymentTabView = new CreatePaymentTabView(paymentController);
        MiningTabView miningTabView = new MiningTabView(miningController, applicationProperties);
        MiningTabView soloMiningTabView = new MiningTabView(soloMiningController, applicationProperties);

        loadWindow.setProgress(loadingCounter++);
        actionController.setMiningController(miningController);
        actionController.setSoloMiningController(soloMiningController);

        MenuBar menuBar = new MenuBar(actionController);
        List<TabContainer> containers = new ArrayList<>();
        URL splashScreenLocation = Thread.currentThread().getContextClassLoader().getResource("stats-icon.png");
        containers.add(new TabContainer<>(0, "Overview", statusTabView, true, new ImageIcon(splashScreenLocation)));
        containers.add(new TabContainer<>(1, "Addresses", addressesTabView, true));
        containers.add(new TabContainer<>(2, "Transactions", transactionsTabView, true));
        containers.add(new TabContainer<>(3, "Payments", paymentTabView, true));
        containers.add(new TabContainer<>(4, "Create payments", createPaymentTabView, true));
        containers.add(new TabContainer<>(5, "Pool Mining", miningTabView, true));
        containers.add(new TabContainer<>(6, "Solo Mining", soloMiningTabView, false));

        LOGGER.info("Generating frame ...");
        MainFrame guiFrame = new MainFrame(menuBar, containers, applicationProperties);
        loadWindow.setProgress(loadingCounter++);

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // Start polling and updating the views
        LOGGER.info("Starting the rpc pollers ...");
        RpcPoller<Status> statusPoller = new NoParamsRpcPoller<>(walletRpcController.getStatusExecutor(), 5000);
        RpcPoller<Addresses> addressesPoller = new NoParamsRpcPoller<>(walletRpcController.getAddressesExecutor(), 5000);
        RpcPoller<BlockCount> syncPoller = new NoParamsRpcPoller<>(coinRpcController.getBlockCountExecutor(), 5000);
        TransactionItemsRpcPoller transactionsPoller = new TransactionItemsRpcPoller(walletRpcController.getTransactionsExecutor(), 5000);
        UnconfirmedTransactionHashesRpcPoller unconfirmedTransactionHashesPoller = new UnconfirmedTransactionHashesRpcPoller(walletRpcController.getUnconfirmedTransactionHashesExecutor(), 5000);
        loadWindow.setProgress(loadingCounter++);


        walletRpcController.addPollers(statusPoller);
        walletRpcController.addPollers(addressesPoller);
        walletRpcController.addPollers(transactionsPoller);
        walletRpcController.addPollers(unconfirmedTransactionHashesPoller);
        coinRpcController.addPollers(syncPoller);
        loadWindow.setProgress(loadingCounter++);

        // Add observers
        transactionsPoller.addObserver(transactionsTabView);
        transactionsPoller.addObserver(statusTabView);
        transactionsPoller.addObserver(paymentTabView);

        unconfirmedTransactionHashesPoller.addObserver(statusTabView);
        unconfirmedTransactionHashesPoller.addObserver(transactionsTabView);

        statusPoller.addObserver(transactionsPoller);
        statusPoller.addObserver(guiFrame);
        statusPoller.addObserver(statusTabView);

        addressesPoller.addObserver(addressesTabView);
        addressesPoller.addObserver(miningTabView);
        addressesPoller.addObserver(soloMiningTabView);
        addressesPoller.addObserver(paymentTabView);
        addressesPoller.addObserver(createPaymentTabView);
        addressesPoller.addObserver(transactionsTabView);
        addressesPoller.addObserver(transactionsPoller);
        addressesPoller.addObserver(unconfirmedTransactionHashesPoller);

        syncPoller.addObserver(guiFrame);
        loadWindow.setProgress(loadingCounter++);

        LOGGER.info("Rpc pollers started.");

        loadWindow.setScreenVisible(false);
        //make sure the JFrame is visible
        guiFrame.setVisible(true);
    }

    private void loadingFrame() {
        URL splashScreenLocation = Thread.currentThread().getContextClassLoader().getResource("splash.png");
        if (splashScreenLocation != null) {
            loadWindow = new SplashWindow(new ImageIcon(splashScreenLocation));
        }
        loadWindow.setLocationRelativeTo(null);
        loadWindow.setProgressMax(10);
        loadWindow.setScreenVisible(true);
    }

    private boolean availableForConnection(int port) {
        try (Socket ignored = new Socket("localhost", port)) {
            return false;
        } catch (IOException ignored) {
            return true;
        }
    }

}
