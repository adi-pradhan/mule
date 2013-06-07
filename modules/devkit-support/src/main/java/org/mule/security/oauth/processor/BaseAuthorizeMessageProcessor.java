/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.security.oauth.processor;

import org.mule.api.DefaultMuleException;
import org.mule.api.MessagingException;
import org.mule.api.MuleEvent;
import org.mule.api.MuleException;
import org.mule.api.construct.FlowConstructAware;
import org.mule.api.context.MuleContextAware;
import org.mule.api.lifecycle.Initialisable;
import org.mule.api.lifecycle.Startable;
import org.mule.api.lifecycle.Stoppable;
import org.mule.api.processor.InterceptingMessageProcessor;
import org.mule.api.processor.MessageProcessor;
import org.mule.api.transformer.TransformerException;
import org.mule.api.transformer.TransformerMessagingException;
import org.mule.common.security.oauth.AuthorizationParameter;
import org.mule.config.i18n.CoreMessages;
import org.mule.security.oauth.DefaultHttpCallback;
import org.mule.security.oauth.HttpCallback;
import org.mule.security.oauth.OAuthAdapter;
import org.mule.security.oauth.OAuthManager;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public abstract class BaseAuthorizeMessageProcessor<T extends OAuthManager<OAuthAdapter>> extends
    AbstractDevkitBasedMessageProcessor<T>
    implements FlowConstructAware, MuleContextAware, Initialisable, Startable, Stoppable,
    InterceptingMessageProcessor
{

    private MessageProcessor listener;
    private String authorizationUrl = null;
    private String accessTokenUrl = null;
    private HttpCallback oauthCallback;
    private String state;

    protected abstract String getAuthCodeRegex();

    protected abstract Class<T> getOAuthManagerClass();

    @Override
    public final void start() throws MuleException
    {
        T moduleObject = null;
        try
        {
            moduleObject = this.findOrCreate(this.getOAuthManagerClass(), false, null);
        }
        catch (IllegalAccessException e)
        {
            throw new DefaultMuleException(CoreMessages.failedToStart("authorize"), e);
        }
        catch (InstantiationException e)
        {
            throw new DefaultMuleException(CoreMessages.failedToStart("authorize"), e);
        }

        this.setModuleObject(moduleObject);

        if (oauthCallback == null)
        {
            FetchAccessTokenMessageProcessor fetchAccessTokenMessageProcessor = new FetchAccessTokenMessageProcessor(
                moduleObject);
            oauthCallback = new DefaultHttpCallback(Arrays.asList(
                new ExtractAuthorizationCodeMessageProcessor(Pattern.compile(this.getAuthCodeRegex())),
                fetchAccessTokenMessageProcessor, listener), getMuleContext(), moduleObject.getDomain(),
                moduleObject.getLocalPort(), moduleObject.getRemotePort(), moduleObject.getPath(),
                moduleObject.getAsync(), getFlowConstruct().getExceptionListener(),
                moduleObject.getConnector());
            fetchAccessTokenMessageProcessor.setRedirectUri(oauthCallback.getUrl());
            if (accessTokenUrl != null)
            {
                fetchAccessTokenMessageProcessor.setAccessTokenUrl(accessTokenUrl);
            }
            else
            {
                fetchAccessTokenMessageProcessor.setAccessTokenUrl(moduleObject.getAccessTokenUrl());
            }
            oauthCallback.start();
        }
    }

    @Override
    public final void stop() throws MuleException
    {
        if (oauthCallback != null)
        {
            oauthCallback.stop();
        }
    }

    private T getOAuthManager()
    {
        try
        {
            return this.findOrCreate(this.getOAuthManagerClass(), false, null);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private Map<String, String> getExtraParameters(MuleEvent event, T moduleObject)
        throws MessagingException, TransformerException
    {
        Set<AuthorizationParameter<?>> params = moduleObject.getDefaultUnauthorizedConnector()
            .getAuthorizationParameters();

        Map<String, String> extraParameters = new HashMap<String, String>();
        if (state != null)
        {
            extraParameters.put("state", this.toString(event, this.state));
        }

        if (params != null)
        {
            for (AuthorizationParameter<?> parameter : params)
            {
                Field field = null;
                try
                {
                    field = this.getClass().getDeclaredField(parameter.getName());
                }
                catch (NoSuchFieldException e)
                {
                    throw new MessagingException(CoreMessages.createStaticMessage(String.format(
                        "Code generation error. Field %s should be present in class", parameter.getName())),
                        event, e);
                }

                field.setAccessible(true);

                try
                {
                    Object value = field.get(this);
                    Object transformed = this.evaluateAndTransform(getMuleContext(), event,
                        parameter.getType(), null, value);
                    extraParameters.put(parameter.getName(), this.toString(event, transformed).toLowerCase());
                }
                catch (IllegalAccessException e)
                {
                    throw new RuntimeException(e);
                }

            }
        }

        return extraParameters;

    }

    /**
     * Starts the OAuth authorization process
     * 
     * @param event MuleEvent to be processed
     * @throws MuleException
     */
    public MuleEvent process(MuleEvent event) throws MuleException
    {
        T moduleObject = null;
        try
        {
            moduleObject = this.getOAuthManager();

            String transformedAuthorizationUrl = this.toString(event, this.authorizationUrl);
            String transformedAccessTokenUrl = this.toString(event, this.accessTokenUrl);

            moduleObject.setAccessTokenUrl(transformedAccessTokenUrl);
            String location = moduleObject.buildAuthorizeUrl(this.getExtraParameters(event, moduleObject),
                transformedAuthorizationUrl, oauthCallback.getUrl());

            event.getMessage().setOutboundProperty("http.status", "302");
            event.getMessage().setOutboundProperty("Location", location);

            return event;
        }
        catch (Exception e)
        {
            throw new MessagingException(CoreMessages.failedToInvoke("authorize"), event, e);
        }
    }

    private String toString(MuleEvent event, Object value)
    {
        try
        {
            return (String) evaluateAndTransform(getMuleContext(), event, String.class, null, value);
        }
        catch (TransformerException e)
        {
            throw new RuntimeException(e);
        }
        catch (TransformerMessagingException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets listener
     * 
     * @param value Value to set
     */
    public void setListener(MessageProcessor value)
    {
        this.listener = value;
    }

    /**
     * Sets authorizationUrl
     * 
     * @param value Value to set
     */
    public void setAuthorizationUrl(String value)
    {
        this.authorizationUrl = value;
    }

    /**
     * Retrieves authorizationUrl
     */
    public String getAuthorizationUrl()
    {
        return this.authorizationUrl;
    }

    /**
     * Sets accessTokenUrl
     * 
     * @param value Value to set
     */
    public void setAccessTokenUrl(String value)
    {
        this.accessTokenUrl = value;
    }

    /**
     * Retrieves accessTokenUrl
     */
    public String getAccessTokenUrl()
    {
        return this.accessTokenUrl;
    }

    /**
     * Sets state
     * 
     * @param value Value to set
     */
    public void setState(String value)
    {
        this.state = value;
    }

}