/*
 * Copyright 2016 John Grosh (jagrosh).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.vortex.commands.moderation;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.vortex.Vortex;
import com.jagrosh.vortex.commands.ModCommand;
import net.dv8tion.jda.core.Permission;
import com.jagrosh.vortex.utils.ArgsUtil;
import com.jagrosh.vortex.utils.ArgsUtil.ResolvedArgs;
import com.jagrosh.vortex.utils.FormatUtil;
import com.jagrosh.vortex.utils.LogUtil;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 *
 * @author John Grosh (jagrosh)
 */
public class BanCmd extends ModCommand
{
    public BanCmd(Vortex vortex)
    {
        super(vortex, Permission.BAN_MEMBERS);
        this.name = "ban";
        this.aliases = new String[]{"hackban","forceban"};
        this.arguments = "<@users...> [time] [reason]";
        this.help = "bans all provided users";
        this.botPermissions = new Permission[]{Permission.BAN_MEMBERS};
        this.guildOnly = true;
    }

    @Override
    protected void execute(CommandEvent event)
    {
        ResolvedArgs args = ArgsUtil.resolve(event.getArgs(), true, event.getGuild());
        if(args.isEmpty())
        {
            event.replyError("Please include at least one user to ban (@mention or ID)!");
            return;
        }
        int minutes = args.time/60;
        if(minutes < 0)
        {
            event.replyError("Timed bans cannot be negative time!");
            return;
        }
        String reason = LogUtil.auditReasonFormat(event.getMember(), minutes, args.reason);
        StringBuilder builder = new StringBuilder();
        
        args.members.forEach(m -> 
        {
            if(!event.getMember().canInteract(m))
                builder.append("\n").append(event.getClient().getError()).append(" You do not have permission to ban ").append(FormatUtil.formatUser(m.getUser()));
            else if(!event.getSelfMember().canInteract(m))
                builder.append("\n").append(event.getClient().getError()).append(" I am unable to ban ").append(FormatUtil.formatUser(m.getUser()));
            else
                args.ids.add(m.getUser().getIdLong());
        });
        
        args.unresolved.forEach(un -> builder.append("\n").append(event.getClient().getWarning()).append(" Could not resolve `").append(un).append("` to a user ID"));
        
        args.users.forEach(u -> args.ids.add(u.getIdLong()));
        
        if(args.ids.isEmpty())
        {
            event.reply(builder.toString());
            return;
        }
        
        if(args.ids.size() > 5)
            event.reactSuccess();
        
        Instant unbanTime = Instant.now().plus(minutes, ChronoUnit.MINUTES);
        for(int i=0; i<args.ids.size(); i++)
        {
            long uid = args.ids.get(i);
            String id = Long.toString(uid);
            boolean last = i+1 == args.ids.size();
            event.getGuild().getController().ban(id, 1, reason).queue(success -> 
            {
                builder.append("\n").append(event.getClient().getSuccess()).append(" Successfully banned <@").append(id).append(">");
                if(minutes>0)
                    vortex.getDatabase().tempbans.setBan(event.getGuild(), uid, unbanTime);
                if(last)
                    event.reply(builder.toString());
            }, failure -> 
            {
                builder.append("\n").append(event.getClient().getError()).append(" Failed to ban <@").append(id).append(">");
                if(last)
                    event.reply(builder.toString());
            });
        }
    }
}