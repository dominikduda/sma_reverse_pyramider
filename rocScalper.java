package jforex;

import com.dukascopy.api.*;
import java.math.*;
import java.text.*;
import java.util.*;
import java.util.HashSet;
import java.util.Set;
import java.util.Random;

public class IntradayPositionCloser implements IStrategy {
  private IEngine engine;
  private IConsole console;
  private IContext context;
  private IHistory history;
  private IIndicators indicators;
  private IUserInterface userInterface;

  @Configurable("Instrument")
  public Instrument instrument = null;

  @Configurable("Limit level after 7 bars")
  public double limitLevelFor7Bars = -1;

  @Configurable("Safety limit level for close")
  public double limitLevelForClose = -1;

  @Configurable("Minimal profit pips")
  public double minimalProfitPips = 2;

  @Configurable("Ignore initial candle for 'reverse color' close")
  public boolean ignoreInitialCandle = false;

  private int closedM15CandlesTillBotStart = 0;
  private boolean shouldCloseAllOrders = false;

  private double extremeForTrail = -1;

  public void onStart(IContext context) throws JFException {
    if (instrument == null) {
      throw new JFException("You must provide instrumetnt");
    }
    // if (limitLevelFor7Bars == -1) {
    //   throw new JFException("You must provide limit level for 8 bars");
    // }
    this.console = context.getConsole();
    if (limitLevelForClose == -1) {
      console.getOut().println("Exit after close flips level disabled by argument (-1)");
    }
    this.engine = context.getEngine();
    this.history = context.getHistory();
    this.context = context;
    this.indicators = context.getIndicators();
    this.userInterface = context.getUserInterface();
    // Do subscribe selected instrument
    Set subscribedInstruments = new HashSet();
    subscribedInstruments.add(this.instrument);
    context.setSubscribedInstruments(subscribedInstruments);
    Random rand = new Random();
    // between 0 and 99
    int n = rand.nextInt(100);
    // close more then half of trades at 4.5 pips profit
    if (n > 45) {
      console.getOut().println("Randomed this order to use 4.5 pips TP");
      IOrder sampleOrder = engine.getOrders(this.instrument).get(0);
      if (isLong()) {
        setAllTakeProfitsTo(sampleOrder.getOpenPrice() + instrument.getPipValue() * 4.5);
      }
      if (!isLong()) {
        setAllTakeProfitsTo(sampleOrder.getOpenPrice() - instrument.getPipValue() * 4.5);
      }
    }
  }

  public void onAccount(IAccount account) throws JFException {}

  public void onMessage(IMessage message) throws JFException {
    // Print messages, but related to own orders
    if (message.getOrder() != null) {
      if (message.getOrder().getInstrument() != this.instrument) {
        return;
      }
      IMessage.Type messageType = message.getType();
      switch (messageType) {
        case ORDER_FILL_OK:
          break;
        case ORDER_CHANGED_OK:
          break;
        case ORDER_SUBMIT_OK:
        case ORDER_CLOSE_OK:

        case ORDERS_MERGE_OK:
          break;
        case NOTIFICATION:
          break;
        case ORDER_CHANGED_REJECTED:
        case ORDER_CLOSE_REJECTED:
        case ORDER_FILL_REJECTED:
        case ORDER_SUBMIT_REJECTED:
        case ORDERS_MERGE_REJECTED:
          break;
        default:
          break;
      }
    }
  }

  public void onStop() throws JFException {}

  public void onTick(Instrument instrument, ITick tick) throws JFException {
    if (instrument != this.instrument) {
      return;
    }

    if (filledOrdersCount() == 0) {
      console.getOut().println("No orders present. Script stopped.");
      context.stop();
    }

    if (this.shouldCloseAllOrders) {
      closeAllOrders();
    }
  }

  public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
    if (instrument != this.instrument) {
      return;
    }

    if (period != Period.FIFTEEN_MINS) {
      return;
    }

    // handle close in loss because limit level exceeded
    if (limitLevelForClose != -1) { // disabled - for mini stop
      IBar relevantBarForClose = null;
      if (isLong()) {
        relevantBarForClose = bidBar;
        if (relevantBarForClose.getClose() <= limitLevelForClose) {
          console.getOut().println("Close not above the safety. Exiting position.");
          closeAllOrders();
        }
      }
      if (!isLong()) {
        relevantBarForClose = askBar;
        if (relevantBarForClose.getClose() >= limitLevelForClose) {
          console.getOut().println("Close not below the safety level. Exiting position.");
          closeAllOrders();
        }
      }
    }

    // handle time out by 7 candles close
    this.closedM15CandlesTillBotStart++;
    if (limitLevelFor7Bars != -1) {
      IBar relevantBarForTimeout = null;
      if (isLong() && closedM15CandlesTillBotStart == 7) {
        relevantBarForTimeout = bidBar;
        if (relevantBarForTimeout.getClose() < limitLevelFor7Bars) {
          console.getOut().println("Too little profit in 7 candles. Exiting position.");
          closeAllOrders();
        }
      }
      if (!isLong() && closedM15CandlesTillBotStart == 7) {
        relevantBarForTimeout = askBar;
        if (relevantBarForTimeout.getClose() > limitLevelFor7Bars) {
          console.getOut().println("Too little profit in 7 candles. Exiting position.");
          closeAllOrders();
        }
      }
    }

    // handle opposite bar in profit
    IOrder sampleOrder = engine.getOrders(this.instrument).get(0);
    IBar relevantBar = null;
    boolean skipCheckForThisCandle = ignoreInitialCandle && this.closedM15CandlesTillBotStart == 1;
    if (isLong() && !skipCheckForThisCandle) {
      relevantBar = bidBar;
      boolean isRed = relevantBar.getClose() <= relevantBar.getOpen();
      if (sampleOrder.getProfitLossInPips() > minimalProfitPips && isRed) {
        console.getOut().println("Red bar closing on profit detected. Exiting position");
        closeAllOrders();
      }
    }
    if (!isLong() && !skipCheckForThisCandle) {
      relevantBar = askBar;
      boolean isGreen = relevantBar.getClose() >= relevantBar.getOpen();
      if (sampleOrder.getProfitLossInPips() > minimalProfitPips && isGreen) {
        console.getOut().println("Green bar closing on profit detected. Exiting position");
        closeAllOrders();
      }
    }
  }

  protected double getRoundedPrice(double price) {
    BigDecimal bd = new BigDecimal(price);
    bd = bd.setScale(this.instrument.getPipScale() + 1, RoundingMode.HALF_UP);
    return bd.doubleValue();
  }

  protected void closeAllOrders() throws JFException {
    for (IOrder order : engine.getOrders(this.instrument)) {
      order.close();
    }
    this.shouldCloseAllOrders = true;
  }

  protected int filledOrdersCount() throws JFException {
    int filledOrdersTotal = 0;
    for (IOrder order : engine.getOrders(this.instrument)) {
      if (order.getState() == IOrder.State.FILLED) {
        filledOrdersTotal += 1;
      }
    }
    return filledOrdersTotal;
  }

  protected int totalOrdersCount() throws JFException {
    int filledOrdersTotal = 0;
    for (IOrder order : engine.getOrders(this.instrument)) {
      filledOrdersTotal += 1;
    }
    return filledOrdersTotal;
  }

  void setAllStopLossesTo(double price) throws JFException {
    for (IOrder order : engine.getOrders(this.instrument)) {
      if (isLong() && getRoundedPrice(price) > order.getStopLossPrice()) {
        order.setStopLossPrice(getRoundedPrice(price));
      }
      if (!isLong() && getRoundedPrice(price) < order.getStopLossPrice()) {
        order.setStopLossPrice(getRoundedPrice(price));
      }
      order.waitForUpdate(2000);
      order.waitForUpdate(2000);
    }
  }

  void setAllTakeProfitsTo(double price) throws JFException {
    for (IOrder order : engine.getOrders(this.instrument)) {
      order.setTakeProfitPrice(price);
      order.waitForUpdate(2000);
      order.waitForUpdate(2000);
    }
  }

  // void handleTooMuchRisk() throws JFException {
  //   int nonBreakEvenOrdersCount = 0;
  //   for (IOrder order : engine.getOrders(this.instrument)) {
  //     if (order.getState() == IOrder.State.FILLED) {
  //       if (isLong()) {
  //         if (order.getOpenPrice() > order.getStopLossPrice()) {
  //           nonBreakEvenOrdersCount++;
  //         }
  //       } else {
  //         if (order.getOpenPrice() < order.getStopLossPrice()) {
  //           nonBreakEvenOrdersCount++;
  //         }
  //       }
  //     }
  //   }
  //   if (nonBreakEvenOrdersCount > 2 && entriesClosedByRiskManagement <
  // maxEntriesClosedByRiskManagement) {
  //     entriesClosedByRiskManagement++;
  //     orderWithBiggestProfit().close();
  //   }
  // }

  // IOrder orderWithBiggestProfit() throws JFException {
  //   IOrder result = null;
  //   for (IOrder order : engine.getOrders(this.instrument)) {
  //     if (order.getState() == IOrder.State.FILLED) {
  //       if (result == null || result.getProfitLossInUSD() < order.getProfitLossInUSD()) {
  //         result = order;
  //       }
  //     }
  //   }
  //   return result;
  // }

  boolean isLong() throws JFException {
    List<IOrder> orders = engine.getOrders(this.instrument);
    return orders.get(0).isLong();
  }

  boolean barColorRightForFastTrails(IBar bar) throws JFException {
    boolean isRed = bar.getClose() < bar.getOpen();
    boolean result = false;
    if (isLong()) {
      if (!isRed) {
        result = true;
      }
    }
    if (!isLong()) {
      if (isRed) {
        result = true;
      }
    }
    return result;
  }

  protected IBar getBar(Period timeFrame, OfferSide marketSide, int index) throws JFException {
    IBar lastBar = history.getBar(this.instrument, timeFrame, marketSide, index);
    return (lastBar);
  }

  OfferSide stopLossOfferSide() throws JFException {
    if (isLong()) {
      return OfferSide.BID;
    } else {
      return OfferSide.ASK;
    }
  }

  protected boolean canEnterLong() throws JFException {
    double SMAflashH4 =
        indicators.sma(
            instrument, Period.FOUR_HOURS, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 6, 0);
    double SMAsecondH4 =
        indicators.sma(
            instrument, Period.FOUR_HOURS, OfferSide.ASK, IIndicators.AppliedPrice.CLOSE, 14, 0);
    if (SMAflashH4 >= SMAsecondH4) {
      return true;
    } else {
      return false;
    }
  }
}
