import 'dotenv/config';
import {
  Client,
  GatewayIntentBits,
  Partials,
  ChannelType,
  PermissionFlagsBits,
  SlashCommandBuilder,
  ActionRowBuilder,
  ButtonBuilder,
  ButtonStyle,
  EmbedBuilder,
  REST,
  Routes,
  MessageFlags,
} from 'discord.js';

/**
 * Tiny N Arena Discord bot.
 * Intents are kept minimal. Tickets use private threads to avoid channel spam.
 */

const TOKEN = process.env.DISCORD_TOKEN;
const CLIENT_ID = process.env.CLIENT_ID;
const GUILD_ID = process.env.GUILD_ID;

if (!TOKEN) {
  console.error('Set DISCORD_TOKEN in .env');
  process.exit(1);
}

const CHANNELS = [
  { cat: 'INFO', names: ['welcome', 'rules', 'announcements', 'updates', 'maintenance', 'faq'] },
  { cat: 'COMPETE', names: ['duel', 'leaderboards', 'stats'] },
  { cat: 'SUPPORT', names: ['report', 'bug-report', 'unban-appeal', 'other-ticket'] },
  { cat: 'COMMUNITY', names: ['chat', 'clips'] },
];

const client = new Client({
  intents: [GatewayIntentBits.Guilds, GatewayIntentBits.GuildMessages],
  partials: [Partials.Channel],
});

async function ensureChannels(guild) {
  const byName = new Map(guild.channels.cache.map((c) => [c.name, c]));
  for (const block of CHANNELS) {
    let category = [...guild.channels.cache.values()].find(
      (c) => c.type === ChannelType.GuildCategory && c.name === block.cat,
    );
    if (!category) {
      category = await guild.channels.create({
        name: block.cat,
        type: ChannelType.GuildCategory,
      });
    }
    for (const name of block.names) {
      if (byName.has(name)) continue;
      await guild.channels.create({
        name,
        type: ChannelType.GuildText,
        parent: category.id,
        topic: `N Arena · #${name}`,
      });
    }
  }
}

async function postTicketPanels(guild) {
  const tickets = [
    { ch: 'report', label: 'Report Player', style: ButtonStyle.Danger, id: 'ticket:report' },
    { ch: 'bug-report', label: 'Bug Report', style: ButtonStyle.Primary, id: 'ticket:bug' },
    { ch: 'unban-appeal', label: 'Unban Appeal', style: ButtonStyle.Secondary, id: 'ticket:unban' },
    { ch: 'other-ticket', label: 'Other Ticket', style: ButtonStyle.Success, id: 'ticket:other' },
  ];
  for (const t of tickets) {
    const channel = guild.channels.cache.find((c) => c.name === t.ch && c.isTextBased());
    if (!channel) continue;
    const row = new ActionRowBuilder().addComponents(
      new ButtonBuilder().setCustomId(t.id).setLabel(t.label).setStyle(t.style),
    );
    await channel.send({
      embeds: [
        new EmbedBuilder()
          .setColor(0xffd54f)
          .setTitle(`N Arena · ${t.label}`)
          .setDescription('ボタンを押してチケット（プライベートスレッド）を作成します。'),
      ],
      components: [row],
    });
  }
}

client.once('ready', () => {
  console.log(`[N Arena] logged in as ${client.user.tag}`);
});

client.on('interactionCreate', async (interaction) => {
  try {
    if (interaction.isChatInputCommand()) {
      if (interaction.commandName === 'setup') {
        if (!interaction.memberPermissions?.has(PermissionFlagsBits.Administrator)) {
          await interaction.reply({ content: 'Admin only.', flags: MessageFlags.Ephemeral });
          return;
        }
        await interaction.deferReply({ flags: MessageFlags.Ephemeral });
        await ensureChannels(interaction.guild);
        await postTicketPanels(interaction.guild);
        await interaction.editReply('Channels + ticket panels ready.');
        return;
      }
      if (interaction.commandName === 'stats') {
        const name = interaction.options.getString('player', true);
        await interaction.reply({
          embeds: [
            new EmbedBuilder()
              .setColor(0xffd54f)
              .setTitle(`Profile · ${name}`)
              .setDescription(
                'ゲーム側 API 連携前のプレースホルダです。\n' +
                  'RumilancePractice の webhook/HTTP を繋ぐと Elo・連勝を埋められます。',
              ),
          ],
          flags: MessageFlags.Ephemeral,
        });
        return;
      }
    }

    if (interaction.isButton() && interaction.customId.startsWith('ticket:')) {
      const kind = interaction.customId.slice('ticket:'.length);
      const thread = await interaction.channel.threads.create({
        name: `${kind}-${interaction.user.username}`.slice(0, 90),
        type: ChannelType.PrivateThread,
        invitable: false,
        reason: `ticket ${kind}`,
      });
      await thread.members.add(interaction.user.id);
      await thread.send({
        content: `${interaction.user} の **${kind}** チケットです。内容を書いてください。運営が対応します。`,
      });
      await interaction.reply({ content: `Ticket opened: ${thread}`, flags: MessageFlags.Ephemeral });
    }
  } catch (err) {
    console.error(err);
    if (interaction.isRepliable() && !interaction.replied) {
      await interaction.reply({ content: 'Error.', flags: MessageFlags.Ephemeral }).catch(() => {});
    }
  }
});

/** Optional HTTP hook for the Minecraft plugin to push duel kill lines (low traffic). */
import http from 'node:http';
const PORT = Number(process.env.HOOK_PORT || 0);
if (PORT > 0) {
  http
    .createServer(async (req, res) => {
      if (req.method !== 'POST' || req.url !== '/duel') {
        res.writeHead(404);
        res.end();
        return;
      }
      let body = '';
      for await (const chunk of req) body += chunk;
      try {
        const { guildId, text } = JSON.parse(body);
        const guild = await client.guilds.fetch(guildId || GUILD_ID);
        const duel = guild.channels.cache.find((c) => c.name === 'duel' && c.isTextBased());
        if (duel) await duel.send({ content: String(text).slice(0, 1800) });
        res.writeHead(204);
        res.end();
      } catch (e) {
        res.writeHead(400);
        res.end('bad');
      }
    })
    .listen(PORT, () => console.log(`[N Arena] duel hook :${PORT}`));
}

const commands = [
  new SlashCommandBuilder().setName('setup').setDescription('Create N Arena channels and ticket panels'),
  new SlashCommandBuilder()
    .setName('stats')
    .setDescription('Look up a player profile placeholder')
    .addStringOption((o) => o.setName('player').setDescription('MCID').setRequired(true)),
].map((c) => c.toJSON());

async function register() {
  if (!CLIENT_ID) return;
  const rest = new REST({ version: '10' }).setToken(TOKEN);
  if (GUILD_ID) {
    await rest.put(Routes.applicationGuildCommands(CLIENT_ID, GUILD_ID), { body: commands });
  } else {
    await rest.put(Routes.applicationCommands(CLIENT_ID), { body: commands });
  }
  console.log('[N Arena] slash commands registered');
}

await register();
await client.login(TOKEN);
