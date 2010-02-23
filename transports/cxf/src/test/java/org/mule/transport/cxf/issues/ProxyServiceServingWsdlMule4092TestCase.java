/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSource, Inc.  All rights reserved.  http://www.mulesource.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.transport.cxf.issues;

import org.mule.tck.FunctionalTestCase;
import org.mule.util.IOUtils;
import org.mule.util.SystemUtils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.custommonkey.xmlunit.XMLUnit;

public class ProxyServiceServingWsdlMule4092TestCase extends FunctionalTestCase
{
    private String expectedWsdlFileName;
    
    @Override
    protected void doSetUp() throws Exception
    {
        super.doSetUp();
        XMLUnit.setIgnoreWhitespace(true);        
        setupExpectedWsdlFileName();
    }

    /**
     * The WSDL generated by CXF is basically the same but slightly differs in whitespace and
     * element ordering (which does not matter). XMLUnit's javadoc says it can ignore element
     * ordering but obviously that does not work, hence this hack.
     */
    private void setupExpectedWsdlFileName()
    {
        if (SystemUtils.isSunJDK() || SystemUtils.isAppleJDK())
        {
            expectedWsdlFileName = "test.wsdl";
        }
        else if (SystemUtils.isIbmJDK())
        {
            if (SystemUtils.isJavaVersionAtLeast(160))
            {
                expectedWsdlFileName = "test.wsdl.ibmjdk-6";
            }
            else
            {
                expectedWsdlFileName = "test.wsdl.ibmjdk-5";
            }
        }
        else
        {
            fail("Unknown JDK");
        }
    }

    @Override
    protected String getConfigResources()
    {
        return "issues/proxy-service-serving-wsdl-mule4092.xml";
    }

    public void testProxyServiceWSDL() throws MalformedURLException, IOException, Exception
    {
        String expected = getXML("issues/" + expectedWsdlFileName);
        
        URL url = new URL("http://localhost:8777/services/onlinestore?wsdl");
        String wsdlFromService = IOUtils.toString(url.openStream());
        assertTrue(compareResults(expected, wsdlFromService));
    }

    protected String getXML(String requestFile) throws Exception
    {
        String xml = IOUtils.toString(IOUtils.getResourceAsStream(requestFile, this.getClass()), "UTF-8");
        if (xml != null)
        {
            return xml;
        }
        else
        {
            fail("Unable to load test request file");
            return null;
        }
    }

    protected boolean compareResults(String expected, String result)
    {
        try
        {
            String expectedString = this.normalizeString(expected);
            String resultString = this.normalizeString(result);
            return XMLUnit.compareXML(expectedString, resultString).similar();
        }
        catch (Exception ex)
        {
            return false;
        }
    }

    protected String normalizeString(String rawString)
    {
        rawString = rawString.replaceAll("\r", "");
        rawString = rawString.replaceAll("\n", "");
        return rawString.replaceAll("\t", "");
    }
}
