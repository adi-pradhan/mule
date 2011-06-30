/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.transport.ftp.reliability;

import org.mule.exception.DefaultSystemExceptionStrategy;
import org.mule.routing.filters.WildcardFilter;
import org.mule.tck.probe.PollingProber;
import org.mule.tck.probe.Probe;
import org.mule.tck.probe.Prober;
import org.mule.transport.ftp.AbstractFtpServerTestCase;

/**
 * Verify that no inbound messages are lost when exceptions occur.  
 * The message must either make it all the way to the SEDA queue (in the case of 
 * an asynchronous inbound endpoint), or be restored/rolled back at the source.
 * 
 * In the case of FTP, this will cause the postProcess() method to not be executed 
 * and therefore the source file will not be deleted.
 */
public class InboundMessageLossTestCase extends AbstractFtpServerTestCase
{
    /** Polling mechanism to replace Thread.sleep() for testing a delayed result. */
    protected Prober prober = new PollingProber(10000, 100);
        
    @Override
    protected String getConfigResources()
    {
        return "reliability/inbound-message-loss.xml";
    }

    @Override
    protected void doSetUp() throws Exception
    {
        super.doSetUp();
        
        // Set SystemExceptionStrategy to redeliver messages (this can only be configured programatically for now)
        ((DefaultSystemExceptionStrategy) muleContext.getExceptionListener()).setRollbackTxFilter(new WildcardFilter("*"));

        // Create a separate source directory for each test case
        createFtpServerDir("noException");
        createFtpServerDir("transformerException");
        createFtpServerDir("routerException");
        createFtpServerDir("componentException");
    }

    public void testNoException() throws Exception
    {
        createFileOnFtpServer("noException/test1");
        prober.check(new Probe()
        {
            public boolean isSatisfied()
            {
                // Delivery was successful so message should be gone
                return !fileExists("noException/test1");
            }

            public String describeFailure()
            {
                return "File should be gone";
            }
        });
    }
    
    public void testTransformerException() throws Exception
    {
        createFileOnFtpServer("transformerException/test1");
        prober.check(new Probe()
        {
            public boolean isSatisfied()
            {
                // Delivery failed so message should have been restored at the source
                return fileExists("transformerException/test1");
            }

            public String describeFailure()
            {
                return "File should have been restored";
            }
        });
    }
    
    public void testRouterException() throws Exception
    {
        createFileOnFtpServer("routerException/test1");
        prober.check(new Probe()
        {
            public boolean isSatisfied()
            {
                // Delivery failed so message should have been restored at the source
                return fileExists("routerException/test1");
            }

            public String describeFailure()
            {
                return "File should have been restored";
            }
        });
    }
    
    public void testComponentException() throws Exception
    {
        createFileOnFtpServer("componentException/test1");
        prober.check(new Probe()
        {
            public boolean isSatisfied()
            {
                // Component exception occurs after the SEDA queue for an asynchronous request, so from the client's
                // perspective, the message has been delivered successfully.
                return !fileExists("componentException/test1");
            }

            public String describeFailure()
            {
                return "File should be gone";
            }
        });
    }    
}