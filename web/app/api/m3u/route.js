import { NextResponse } from 'next/server';

export async function GET(request) {
  const { searchParams } = new URL(request.url);
  const username = searchParams.get('username');
  const token = searchParams.get('token');
  const source = process.env.M3U_SOURCE_URL;

  if (!username || !token) {
    return new NextResponse('#EXTM3U\n# Missing username or token\n', {
      status: 400,
      headers: { 'Content-Type': 'audio/x-mpegurl; charset=utf-8' }
    });
  }

  if (!source) {
    return new NextResponse('#EXTM3U\n# M3U source not configured on server\n', {
      status: 503,
      headers: { 'Content-Type': 'audio/x-mpegurl; charset=utf-8' }
    });
  }

  try {
    const response = await fetch(source, { cache: 'no-store' });
    if (!response.ok) {
      return new NextResponse('#EXTM3U\n# Authorized M3U source unavailable\n', {
        status: 502,
        headers: { 'Content-Type': 'audio/x-mpegurl; charset=utf-8' }
      });
    }
    const playlist = await response.text();
    return new NextResponse(playlist, {
      status: 200,
      headers: {
        'Content-Type': 'audio/x-mpegurl; charset=utf-8',
        'Content-Disposition': 'inline; filename="playlist.m3u"',
        'Cache-Control': 'no-store'
      }
    });
  } catch {
    return new NextResponse('#EXTM3U\n# Unable to reach authorized M3U source\n', {
      status: 502,
      headers: { 'Content-Type': 'audio/x-mpegurl; charset=utf-8' }
    });
  }
}
